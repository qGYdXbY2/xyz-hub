/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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

package com.here.xyz.hub.rest;

import static com.here.xyz.hub.auth.TestAuthenticator.AuthProfile.ACCESS_ALL;
import static com.here.xyz.util.service.BaseHttpServerVerticle.HeaderValues.APPLICATION_GEO_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.here.xyz.models.geojson.coordinates.PointCoordinates;
import com.here.xyz.models.geojson.implementation.Feature;
import com.here.xyz.models.geojson.implementation.FeatureCollection;
import com.here.xyz.models.geojson.implementation.Point;
import com.here.xyz.models.geojson.implementation.Properties;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(RestTests.class)
public class VersioningGetFeaturesIT extends TestSpaceWithFeature {

  protected static final String SPACE_ID = "spacev2k1000";
  protected static final String AUTHOR_1 = "XYZ-01234567-89ab-cdef-0123-456789aUSER1";
  protected static final String AUTHOR_2 = "XYZ-01234567-89ab-cdef-0123-456789aUSER2";

  @Before
  public void before() {
    removeSpace(SPACE_ID);
    createSpaceWithVersionsToKeep("spacev2k1000", 1000);
    postFeature(SPACE_ID, newFeature(), ACCESS_ALL);
    postFeature(SPACE_ID, newFeature()
        .withGeometry(new Point().withCoordinates(new PointCoordinates(50,50)))
        .withProperties(new Properties().with("key2", "value2")), ACCESS_ALL);
  }

  @After
  public void after() {
    removeSpace(SPACE_ID);
  }

  @Test
  public void testGetBboxWithVersion() {
    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/bbox?west=1&south=-1&east=-1&north=1&version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue())
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(1));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/bbox?west=51&south=49&east=49&north=51&version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/bbox?west=1&south=-1&east=-1&north=1&version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/bbox?west=51&south=49&east=49&north=51&version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(2));
  }

  @Test
  public void testGetTileWithVersion() {
    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/tile/quadkey/03333?version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue())
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(1));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/tile/quadkey/12120?version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/tile/quadkey/03333?version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/tile/quadkey/12120?version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(2));
  }

  @Test
  public void testGetSpatialWithVersion() {
    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/spatial?lat=0&lon=0&radius=10000&version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue())
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(1));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/spatial?lat=50&lon=50&radius=10000&version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/spatial?lat=0&lon=0&radius=10000&version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/spatial?lat=50&lon=50&radius=10000&version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(2));
  }

  @Test
  public void testPostSpatialWithVersion() {
    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .contentType(APPLICATION_GEO_JSON)
        .when()
        .body("{\"type\":\"Point\",\"coordinates\":[1,1,0]}")
        .post(getSpacesPath() + "/" + SPACE_ID + "/spatial?radius=1500000&version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue())
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(1));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .contentType(APPLICATION_GEO_JSON)
        .when()
        .body("{\"type\":\"Point\",\"coordinates\":[49,49,0]}")
        .post(getSpacesPath() + "/" + SPACE_ID + "/spatial?radius=1500000&version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(2));
  }

  @Test
  public void testGetSearchWithVersion() {
    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=1&p.key1=value1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue())
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(1));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=1&p.key2=value2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=2&p.key1=value1&p.key2=value2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(2));
  }

  @Test
  public void testGetIterateWithVersion() {
    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/iterate?version=1&limit=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo("f1"))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue());

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/iterate?version=2&limit=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo("f1"))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"));
  }

  @Test
  public void testGetIteratePagingWithVersion() {
    postFeatures(SPACE_ID, new FeatureCollection().withFeatures(new ArrayList<>() {{
      add(new Feature()
          .withId("IT1")
          .withGeometry(new Point().withCoordinates(new PointCoordinates(1,1)))
          .withProperties(new Properties().with("it1", "it1")));

      add(new Feature()
          .withId("IT2")
          .withGeometry(new Point().withCoordinates(new PointCoordinates(2,2)))
          .withProperties(new Properties().with("it2", "it2")));

      add(new Feature()
          .withId("IT3")
          .withGeometry(new Point().withCoordinates(new PointCoordinates(3,3)))
          .withProperties(new Properties().with("it3", "it3")));
    }}),
        ACCESS_ALL);

    String handle = given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/iterate?version=3&limit=3")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(3))
        .body("features.id", contains("f1", "IT1", "IT2"))
        .extract()
        .path("nextPageToken");

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/iterate?version=3&handle=" + handle)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features.id", contains("IT3"));
  }

  @Test
  public void testGetSearchWithAuthor() {
    postFeature(SPACE_ID, newFeature().withProperties(new Properties().with("population", 5000)), AuthProfile.ACCESS_OWNER_2_ALL);

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=1&author=" + AUTHOR_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", nullValue())
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(1));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=4&author=" + AUTHOR_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=2&author=" + AUTHOR_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?version=4&author=" + AUTHOR_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.population", equalTo(5000))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(3));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?p.population=5000&author=" + AUTHOR_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID + "/search?p.population=5000&author=" + AUTHOR_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].properties.key1", equalTo("value1"))
        .body("features[0].properties.key2", equalTo("value2"))
        .body("features[0].properties.population", equalTo(5000))
        .body("features[0].properties.'@ns:com:here:xyz'.version", equalTo(3));
  }
}
