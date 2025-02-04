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

package com.here.xyz.hub.rest;

import static com.here.xyz.hub.auth.TestAuthenticator.AuthProfile.ACCESS_OWNER_1_ADMIN;
import static com.here.xyz.util.service.BaseHttpServerVerticle.HeaderValues.APPLICATION_GEO_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

import com.here.xyz.models.geojson.implementation.Feature;
import com.here.xyz.models.geojson.implementation.FeatureCollection;
import com.here.xyz.models.geojson.implementation.Properties;
import com.here.xyz.models.geojson.implementation.XyzNamespace;
import com.here.xyz.util.web.HubWebClient;
import com.here.xyz.util.web.XyzWebClient.WebClientException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(RestTests.class)
public class VersioningIT extends TestSpaceWithFeature {
  final String SPACE_ID_1 = "space1";
  final String SPACE_ID_2 = "space2";
  final String FEATURE_ID_1 = "Q3495887";
  final String FEATURE_ID_2 = "Q929126";
  final String FEATURE_ID_3 = "Q1370732";
  final String USER_1 = ACCESS_OWNER_1_ADMIN.payload.aid;
  final String USER_2 = AuthProfile.ACCESS_OWNER_2.payload.aid;

  @Before
  public void setup() {
    remove();
    createSpaceWithVersionsToKeep(SPACE_ID_1, 5);
    createSpaceWithVersionsToKeep(SPACE_ID_2, 5);
    addFeatures(SPACE_ID_1);
    addFeatures(SPACE_ID_2);
    updateFeature(SPACE_ID_1);
    updateFeature(SPACE_ID_2);
  }

  public void updateFeature(String spaceId) {
    // update a feature
    postFeature(spaceId, new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "updated name")),
        ACCESS_OWNER_1_ADMIN);
  }

  @After
  public void tearDown() {
    removeSpace(SPACE_ID_1);
    removeSpace(SPACE_ID_2);
  }

  @Test
  public void testTransactional() {
    Feature f1 = new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "conflicting change").withXyzNamespace(new XyzNamespace().withVersion(0)));
    Feature f2 = new Feature().withId(FEATURE_ID_2).withProperties(new Properties().with("name", "non-conflicting change").withXyzNamespace(new XyzNamespace().withVersion(0)));
    FeatureCollection fc = new FeatureCollection().withFeatures(Arrays.asList(f1, f2));

    given()
        .contentType(APPLICATION_GEO_JSON)
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .body(fc.toString())
        .when()
        .post(getSpacesPath() + "/"+ SPACE_ID_2 +"/features?transactional=true&conflictDetection=true")
        .then()
        .statusCode(CONFLICT.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_2 +"/features/"+ FEATURE_ID_1)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("updated name"))
        .body("properties.@ns:com:here:xyz.version", equalTo(2));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_2 +"/features/"+ FEATURE_ID_2)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo(""))
        .body("properties.@ns:com:here:xyz.version", equalTo(1));
  }

  @Test
  @Ignore("Deprecated functionality")
  public void testNonTransactional() {
    Feature f1 = new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "conflicting change").withXyzNamespace(new XyzNamespace().withVersion(1)));
    Feature f2 = new Feature().withId(FEATURE_ID_2).withProperties(new Properties().with("name", "non-conflicting change").withXyzNamespace(new XyzNamespace().withVersion(1)));
    FeatureCollection fc = new FeatureCollection().withFeatures(Arrays.asList(f1, f2));

    given()
        .contentType(APPLICATION_GEO_JSON)
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .body(fc.toString())
        .when()
        .post(getSpacesPath() + "/"+ SPACE_ID_2 +"/features?transactional=false&conflictDetection=true")
        .then()
        .statusCode(OK.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_2 +"/features/"+ FEATURE_ID_1)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("updated name"))
        .body("properties.@ns:com:here:xyz.version", equalTo(2));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_2 +"/features/"+ FEATURE_ID_2)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("non-conflicting change"))
        .body("properties.@ns:com:here:xyz.version", equalTo(3));
  }

  //@Test
  //TODO fix flickering or remove it
  public void testConflictDetectionDisabled() {
    Feature f1 = new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "conflicting change").withXyzNamespace(new XyzNamespace().withVersion(0)));
    FeatureCollection fc = new FeatureCollection().withFeatures(Collections.singletonList(f1));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .contentType(APPLICATION_GEO_JSON)
        .when()
        .body(fc.toString())
        .post(getSpacesPath() + "/"+ SPACE_ID_1 +"/features")
        .then()
        .statusCode(OK.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_1)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("conflicting change"))
        .body("properties.@ns:com:here:xyz.version", equalTo(2));
  }

  @Test
  public void testConflictDetectionEnabled() {
    Feature f1 = new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "conflicting change").withXyzNamespace(new XyzNamespace().withVersion(1)));
    FeatureCollection fc = new FeatureCollection().withFeatures(Collections.singletonList(f1));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .contentType(APPLICATION_GEO_JSON)
        .when()
        .body(fc.toString())
        .post(getSpacesPath() + "/"+ SPACE_ID_2 +"/features?conflictDetection=true")
        .then()
        .statusCode(CONFLICT.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_2 +"/features/"+ FEATURE_ID_1)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("updated name"))
        .body("properties.@ns:com:here:xyz.version", equalTo(2));
  }

  @Test
  public void testWriteWithVersionInNamespace() {
    Feature f1 = new Feature().withId("F1").withProperties(new Properties().with("name", "abc").withXyzNamespace(new XyzNamespace().withVersion(1)));
    FeatureCollection fc = new FeatureCollection().withFeatures(Collections.singletonList(f1));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .contentType(APPLICATION_GEO_JSON)
        .when()
        .body(fc.toString())
        .post(getSpacesPath() + "/"+ SPACE_ID_1 +"/features")
        .then()
        .statusCode(OK.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/F1?version=1")
        .then()
        .statusCode(NOT_FOUND.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/F1")
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("abc"))
        .body("properties.@ns:com:here:xyz.version", equalTo(3));
  }

  @Test
  public void testWriteWithoutVersionInNamespace() {
    Feature f1 = new Feature().withId("F1").withProperties(new Properties().with("name", "abc"));
    FeatureCollection fc = new FeatureCollection().withFeatures(Collections.singletonList(f1));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .contentType(APPLICATION_GEO_JSON)
        .when()
        .body(fc.toString())
        .post(getSpacesPath() + "/"+ SPACE_ID_1 +"/features")
        .then()
        .statusCode(OK.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/F1?version=1")
        .then()
        .statusCode(NOT_FOUND.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/F1")
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("abc"))
        .body("properties.@ns:com:here:xyz.version", equalTo(3));
  }

  @Test
  public void testMerge() {
    Feature f1 = new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("quantity", 123).withXyzNamespace(new XyzNamespace().withVersion(1)));
    FeatureCollection fc = new FeatureCollection().withFeatures(Collections.singletonList(f1));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .body(fc.toString())
        .post(getSpacesPath() + "/"+ SPACE_ID_1 +"/features")
        .then()
        .statusCode(OK.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_1)
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("updated name"))
        .body("properties.quantity", equalTo(123))
        .body("properties.@ns:com:here:xyz.version", equalTo(3));
  }

  @Test
  public void testGetFeaturesByIdOrderByVersionStar() {
    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_1 +"?version=*")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(2))
        .body("features[0].properties.name", equalTo("Stade Tata Raphaël"))
        .body("features[0].properties.@ns:com:here:xyz.version", equalTo(1))
        .body("features[1].properties.name", equalTo("updated name"))
        .body("features[1].properties.@ns:com:here:xyz.version", equalTo(2));
  }

  @Test
  public void getFeatureExpectVersionIncrease() {
    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_1)
        .then()
        .statusCode(OK.code())
        .body("properties.@ns:com:here:xyz.version", equalTo(2));
  }

  @Test
  public void getFeatureEqualsToVersion() {
    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 +"/features/" + FEATURE_ID_1 + "?version=1")
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("Stade Tata Raphaël"));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/features/"+ FEATURE_ID_1 + "?version=2")
        .then()
        .statusCode(OK.code())
        .body("properties.name", equalTo("updated name"));

//    given()
//        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
//        .when()
//        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/features/" + FEATURE_ID_1 + "?version=222")
//        .then()
//        .statusCode(NOT_FOUND.code()); FIXME should return not found with special hint, that the version is not existing!
  }

  @Test
  public void getFeaturesEqualsToVersion() {
    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/features?id=" + FEATURE_ID_1 + "&id="+FEATURE_ID_2 + "&version=1")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(2))
        .body("features.properties.name", hasItem("Stade Tata Raphaël"))
        .body("features.properties.occupant", hasItem("Guangzhou Evergrande Taobao Football Club"));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/features?id="+  FEATURE_ID_1 + "&id="+FEATURE_ID_2 + "&version=2")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(2))
        .body("features.properties.name", hasItem("updated name"));

//    given()
//        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
//        .when()
//        .get(getSpacesPath() + "/"+ SPACE_ID_1 + "/features?id=" + FEATURE_ID_1 + "&id=" + FEATURE_ID_2 + "&version=222")
//        .then()
//        .statusCode(OK.code())
//        .body("features.size()", equalTo(0)); FIXME should return not found with special hint, that the version is not existing!
  }

  @Test
  public void getFeatureByAuthor() {
    postFeature(SPACE_ID_1, new Feature().withId(FEATURE_ID_2).withProperties(new Properties().with("name", "second feature")), AuthProfile.ACCESS_OWNER_2_ALL);

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_1 +"?author="+USER_1)
        .then()
        .statusCode(OK.code())
        .body("id", equalTo(FEATURE_ID_1))
        .body("properties.name", equalTo("updated name"));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_1 +"?author="+USER_2)
        .then()
        .statusCode(NOT_FOUND.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_2 +"?author="+USER_1)
        .then()
        .statusCode(NOT_FOUND.code());

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features/"+ FEATURE_ID_2 +"?author="+USER_2)
        .then()
        .statusCode(OK.code())
        .body("id", equalTo(FEATURE_ID_2))
        .body("properties.name", equalTo("second feature"));
  }

  @Test
  public void getFeaturesByAuthor() {
    postFeature(SPACE_ID_1, new Feature().withId(FEATURE_ID_2).withProperties(new Properties().with("name", "second feature")), AuthProfile.ACCESS_OWNER_2_ALL);

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&author="+USER_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_1))
        .body("features[0].properties.name", equalTo("updated name"));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&author="+USER_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_2 +"&author="+USER_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_2))
        .body("features[0].properties.name", equalTo("second feature"));
  }

  @Test
  public void getFeaturesEqualsToVersionAndAuthor() {
    postFeature(SPACE_ID_1, new Feature().withId(FEATURE_ID_2).withProperties(new Properties().with("name", "second feature")), AuthProfile.ACCESS_OWNER_2_ALL);

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&id="+FEATURE_ID_2+"&id="+FEATURE_ID_3+"&version=1&author="+USER_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(3))
        .body("features.id", hasItems(FEATURE_ID_1, FEATURE_ID_2, FEATURE_ID_3))
        .body("features.properties.name", hasItems("Stade Tata Raphaël", "", "Estádio Olímpico do Pará"));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&id="+FEATURE_ID_2+"&id="+FEATURE_ID_3+"&version=3&author="+USER_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(2))
        .body("features.id", hasItems(FEATURE_ID_1, FEATURE_ID_3))
        .body("features.properties.name", hasItems("updated name", "Estádio Olímpico do Pará"));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&id="+FEATURE_ID_2+"&id="+FEATURE_ID_3+"&version=1&author="+USER_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&id="+FEATURE_ID_2+"&id="+FEATURE_ID_3+"&version=555&author="+USER_1)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(2));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&id="+FEATURE_ID_2+"&id="+FEATURE_ID_3+"&version=2&author="+USER_2)
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/"+ SPACE_ID_1 +"/features?id="+ FEATURE_ID_1 +"&id="+FEATURE_ID_2+"&id="+FEATURE_ID_3+"&version=3&author="+USER_2)
        .then()
        .statusCode(OK.code())
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_2))
        .body("features[0].properties.name", equalTo("second feature"));
  }

  @Test
  public void searchFeaturesByPropertyAndAuthor() {
    postFeature(SPACE_ID_1, new Feature().withId(FEATURE_ID_2).withProperties(new Properties().with("capacity", 58505)),
        AuthProfile.ACCESS_OWNER_2_ALL);

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/search?version=1&author=" + USER_1 + "&p.capacity=58500")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_2));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/search?version=3&author=" + USER_1 + "&p.capacity=58500")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(0));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/search?version=3&author=" + USER_2 + "&p.capacity>58500")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_2))
        .body("features[0].properties.capacity", equalTo(58505));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/search?version=11&author=" + USER_2 + "&p.capacity=58505")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_2))
        .body("features[0].properties.capacity", equalTo(58505));

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .get(getSpacesPath() + "/" + SPACE_ID_1 + "/search?f.id=" + FEATURE_ID_2 + "&version=3&author=" + USER_2 + "&p.capacity<=58505")
        .then()
        .statusCode(OK.code())
        .body("features.size()", equalTo(1))
        .body("features[0].id", equalTo(FEATURE_ID_2))
        .body("features[0].properties.capacity", equalTo(58505))
        .body("features[0].properties.@ns:com:here:xyz.version", equalTo(3));
  }

  @Test
  public void deleteFeatureAndReinsertTest() throws WebClientException {
    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .when()
        .delete(getSpacesPath() + "/" + SPACE_ID_2 + "/features/" + FEATURE_ID_1 + "?conflictDetection=true")
        .then()
        .statusCode(NO_CONTENT.code());

    long headVersion = HubWebClient.getInstance("http://localhost:8080/hub/").loadSpaceStatistics(SPACE_ID_2).getMaxVersion().getValue();

    postFeature(SPACE_ID_2, new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "updated name 2").withXyzNamespace(new XyzNamespace().withVersion(headVersion))), ACCESS_OWNER_1_ADMIN, true);

    postFeature(SPACE_ID_2, new Feature().withId(FEATURE_ID_1).withProperties(new Properties().with("name", "updated name 3").withXyzNamespace(new XyzNamespace().withVersion(headVersion + 1))), ACCESS_OWNER_1_ADMIN, true);
  }
}
