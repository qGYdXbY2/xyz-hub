/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

package com.here.xyz.hub.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.here.xyz.XyzSerializable;
import com.here.xyz.hub.task.ModifyFeatureOp.FeatureEntry;
import com.here.xyz.hub.task.ModifyOp.Entry;
import com.here.xyz.hub.task.ModifyOp.ModifyOpError;
import com.here.xyz.models.geojson.implementation.Feature;
import com.here.xyz.models.hub.FeatureModificationList.ConflictResolution;
import com.here.xyz.models.hub.FeatureModificationList.IfExists;
import com.here.xyz.models.hub.FeatureModificationList.IfNotExists;
import com.here.xyz.util.service.HttpException;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ModifyFeatureOpTest {

  @Test
  public void patch() {
  }

  @Test
  public void merge() throws IOException, ModifyOpError {
    try (
        final InputStream is1 = ModifyFeatureOpTest.class.getResourceAsStream("/xyz/hub/task/FeatureSample01.json")
    ) {
      final Feature base = XyzSerializable.deserialize(is1);
      final Feature head = base.copy();
      final Feature input = base.copy();

      long now = System.currentTimeMillis();
      head.getProperties().put("newProperty", 1);
      head.getProperties().getXyzNamespace().setVersion(1);
      head.getProperties().getXyzNamespace().setUpdatedAt(now);

      input.getProperties().put("name", "changed");
      input.getProperties().getXyzNamespace().setCreatedAt(123);
      input.getProperties().getXyzNamespace().getTags().add("tag2");

      List<Map<String, Object>> features = Arrays.asList(JsonObject.mapFrom(input).getMap());
      Map<String, Object> featureCollection = Collections.singletonMap("features", features);

      List<FeatureEntry> entries = ModifyFeatureOp.convertToFeatureEntries(Collections.singletonList(Collections.singletonMap("featureData", featureCollection)),
          IfNotExists.CREATE, IfExists.MERGE, ConflictResolution.ERROR);
      ModifyFeatureOp op = new ModifyFeatureOp(entries, true);
      final Entry<Feature> entry = op.entries.get(0);
      entry.head = head;
      entry.base = base;
      Feature res = entry.merge();

      // Expect to be reset to the default value
      assertNotEquals(res.getProperties().getXyzNamespace().getCreatedAt(), 123);
      assertTrue(res.getProperties().getXyzNamespace().getTags().contains("tag1"));
      assertTrue(res.getProperties().getXyzNamespace().getTags().contains("tag2"));
      assertEquals(res.getProperties().get("name"), "changed");
    }
    catch (IOException | ModifyOpError | HttpException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void replace() {
    try (
        final InputStream is1 = ModifyFeatureOpTest.class.getResourceAsStream("/xyz/hub/task/FeatureSample01.json")
    ) {
      final Feature base = XyzSerializable.deserialize(is1);
      final Feature input = base.copy();
      input.getProperties().put("name", "changed");
      input.getProperties().getXyzNamespace().setCreatedAt(123);
      input.getProperties().getXyzNamespace().getTags().add("tag2");

      List<Map<String, Object>> features = Collections.singletonList(JsonObject.mapFrom(input).getMap());
      Map<String, Object> featureCollection = Collections.singletonMap("features", features);

      List<FeatureEntry> entries = ModifyFeatureOp.convertToFeatureEntries(Collections.singletonList(Collections.singletonMap("featureData", featureCollection)),
          IfNotExists.CREATE, IfExists.MERGE, ConflictResolution.ERROR);
      ModifyFeatureOp op = new ModifyFeatureOp(entries, true);
      final Entry<Feature> entry = op.entries.get(0);
      entry.head = base;
      entry.base = base;
      Feature res = entry.replace();

      assertNotEquals(res.getProperties().getXyzNamespace().getCreatedAt(), 123);
      assertTrue(res.getProperties().getXyzNamespace().getTags().contains("tag1"));
      assertTrue(res.getProperties().getXyzNamespace().getTags().contains("tag2"));
      assertEquals(res.getProperties().get("name"), "changed");
    }
    catch (IOException | ModifyOpError | HttpException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void createWithConflictDetection() throws ModifyOpError {
    try (
        final InputStream is1 = ModifyFeatureOpTest.class.getResourceAsStream("/xyz/hub/task/FeatureSample01.json")
    ) {
      final Feature base = XyzSerializable.deserialize(is1);
      final Feature input = base.copy();
      input.getProperties().put("name", "changed");

      List<Map<String, Object>> features = Collections.singletonList(JsonObject.mapFrom(input).getMap());
      Map<String, Object> featureCollection = Collections.singletonMap("features", features);

      List<FeatureEntry> entries = ModifyFeatureOp.convertToFeatureEntries(Collections.singletonList(Collections.singletonMap("featureData", featureCollection)),
          IfNotExists.CREATE, IfExists.MERGE, ConflictResolution.ERROR);
      ModifyFeatureOp op = new ModifyFeatureOp(entries, true);
      final Entry<Feature> entry = op.entries.get(0);
      op.process();
      Feature res = entry.result;
      assertEquals(res.getProperties().get("name"), "changed");
    }
    catch (IOException | HttpException e) {
      e.printStackTrace();
    }
  }
}
