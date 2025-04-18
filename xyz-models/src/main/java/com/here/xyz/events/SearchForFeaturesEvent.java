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

package com.here.xyz.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "SearchForFeaturesEvent")
public class SearchForFeaturesEvent<T extends SearchForFeaturesEvent> extends SelectiveEvent<T> {
  private static final long DEFAULT_LIMIT = 1_000L;
  private static final long MAX_LIMIT = 100_000L;

  private long limit = DEFAULT_LIMIT;
  @JsonIgnore
  public boolean ignoreLimit = false; //TODO: Remove after refactoring

  public long getLimit() {
    return ignoreLimit ? Long.MAX_VALUE : limit;
  }

  @SuppressWarnings("WeakerAccess")
  public void setLimit(long limit) {
    this.limit = Math.max(1L, Math.min(limit, MAX_LIMIT));
  }

  @SuppressWarnings("unused")
  public T withLimit(long limit) {
    setLimit(limit);
    //noinspection unchecked
    return (T) this;
  }

  private PropertiesQuery propertiesQuery;

  @SuppressWarnings("unused")
  public PropertiesQuery getPropertiesQuery() {
    return this.propertiesQuery;
  }

  @SuppressWarnings("WeakerAccess")
  public void setPropertiesQuery(PropertiesQuery propertiesQuery) {
    this.propertiesQuery = propertiesQuery;
  }

  @SuppressWarnings("unused")
  public T withPropertiesQuery(PropertiesQuery propertiesQuery) {
    setPropertiesQuery(propertiesQuery);
    return (T)this;
  }
}
