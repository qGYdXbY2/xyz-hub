/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.connectors.statistics;

import static com.here.xyz.events.PropertyQuery.QueryOperation.GREATER_THAN;
import static com.here.xyz.hub.config.SpaceConfigClient.CONTENT_UPDATED_AT;

import com.google.common.collect.Lists;
import com.here.xyz.events.GetStorageStatisticsEvent;
import com.here.xyz.events.PropertiesQuery;
import com.here.xyz.events.PropertyQuery;
import com.here.xyz.events.PropertyQueryList;
import com.here.xyz.hub.Service;
import com.here.xyz.hub.config.SpaceConfigClient.SpaceAuthorizationCondition;
import com.here.xyz.hub.config.SpaceConfigClient.SpaceSelectionCondition;
import com.here.xyz.hub.connectors.RpcClient;
import com.here.xyz.hub.connectors.models.Connector;
import com.here.xyz.hub.connectors.models.Space;
import com.here.xyz.responses.StorageStatistics;
import com.here.xyz.util.service.Core;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Marker;

public class StorageStatisticsProvider {

  private static final int MAX_CONCURRENT_REQUEST_COUNT = 20;
  private static final int MAX_SPACE_BATCH_SIZE = 50;

  public static Future<StorageStatistics> provideStorageStatistics(Marker marker, long includeChangesSince) {
    SpaceSelectionCondition ssc = new SpaceSelectionCondition();
    PropertyQueryList pql = new PropertyQueryList();
    pql.add(new PropertyQuery()
        .withKey(CONTENT_UPDATED_AT)
        .withOperation(GREATER_THAN)
        .withValues(Collections.singletonList(includeChangesSince)));
    PropertiesQuery pq = new PropertiesQuery();
    pq.add(pql);
    return Service.spaceConfigClient.getSelected(marker, new SpaceAuthorizationCondition(), ssc, pq)
        .compose(spaces -> sortByStorage(spaces))
        .compose(spacesByStorage -> CompositeFuture.all(spacesByStorage
            .entrySet()
            .stream()
            .map(e -> fetchFromStorage(marker, e.getKey(), e.getValue()))
            .collect(Collectors.toList())))
        .compose(results -> Future.succeededFuture(mergeStats(results.list())));
  }

  private static StorageStatistics mergeStats(List<StorageStatistics> stats) {
    StorageStatistics mergedStats = new StorageStatistics()
        .withCreatedAt(Core.currentTimeMillis())
        .withByteSizes(new HashMap<>());
    stats.forEach(s -> {
      if (s == null) return;
      //Use the oldest timestamp in the merged stats
      mergedStats.setCreatedAt(Math.min(s.getCreatedAt(), mergedStats.getCreatedAt()));
      mergedStats.getByteSizes().putAll(s.getByteSizes());
    });
    return mergedStats;
  }

  private static Future<Map<String, List<String>>> sortByStorage(List<Space> spaces) {
    //That operation could take longer, so do it asynchronously
    return Core.vertx.executeBlocking(p -> sortByStorageSync(spaces, p));
  }

  private static void sortByStorageSync(List<Space> spaces, Promise<Map<String, List<String>>> p) {
    Map<String, List<String>> spacesByStorage = new HashMap<>();
    spaces.forEach(space -> {
      final String storageId = space.getStorage().getId();
      if (!spacesByStorage.containsKey(storageId))
        spacesByStorage.put(storageId, new LinkedList<>());
      spacesByStorage.get(storageId).add(resolveSpaceId(space));
    });
    p.complete(spacesByStorage);
  }

  private static String resolveSpaceId(Space space) {
    final String TABLE_NAME = "tableName";
    if (space.getStorage().getParams() != null) {
      Object tableName = space.getStorage().getParams().get(TABLE_NAME);
      if (tableName instanceof String && ((String) tableName).length() > 0)
        return (String) tableName;
    }
    return space.getId();
  }

  private static Future<StorageStatistics> fetchFromStorage(Marker marker, String storageId, List<String> spaceIds) {
    return Space.resolveConnector(marker, storageId)
        //Ignore if the connector is not active or can not be resolved
        .compose(storage -> storage.active && storage.capabilities.storageUtilizationReporting ?
              fetchFromStorage(marker, storage, spaceIds) : Future.succeededFuture(null), t -> Future.succeededFuture(null));
  }

  private static Future<StorageStatistics> fetchFromStorage(Marker marker, Connector storage, List<String> spaceIds) {

    int targetSize = spaceIds.size() > MAX_CONCURRENT_REQUEST_COUNT * MAX_SPACE_BATCH_SIZE
            ? (int) Math.ceil((double) spaceIds.size() / MAX_CONCURRENT_REQUEST_COUNT) : MAX_SPACE_BATCH_SIZE;

    return CompositeFuture.all(Lists.partition(spaceIds, targetSize)
            .stream()
            .map(batchSpaceIds -> fetchFromStorageInBatches(marker, storage, batchSpaceIds))
            .collect(Collectors.toList()))
        .compose(results -> Future.succeededFuture(mergeStats(results.list())));
  }

  private static Future<StorageStatistics> fetchFromStorageInBatches(Marker marker, Connector storage, List<String> spaceIds) {
    Future<List<StorageStatistics>> f = Future.succeededFuture(new ArrayList<>());
    for(List<String> batchSpaceIds : Lists.partition(spaceIds, MAX_SPACE_BATCH_SIZE)) {
      f = f.compose(statsLists -> {
        Promise<List<StorageStatistics>> p = Promise.promise();

        GetStorageStatisticsEvent event = new GetStorageStatisticsEvent()
                .withStreamId(marker.getName())
                .withSpaceIds(batchSpaceIds);
        RpcClient.getInstanceFor(storage).execute(marker, event, true, ar -> {
          if (ar.failed()) p.fail(ar.cause());
          else {
            if (!(ar.result() instanceof StorageStatistics)) p.fail("Wrong response returned by storage " + storage.id);
            else {
              statsLists.add((StorageStatistics) ar.result());
              p.complete(statsLists);
            }
          }
        });
        return p.future();
      });
    }

    return f.compose(statsList -> Future.succeededFuture(mergeStats(statsList)));
  }

}
