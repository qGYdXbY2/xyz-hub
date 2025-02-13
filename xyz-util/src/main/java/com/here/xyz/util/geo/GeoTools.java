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

package com.here.xyz.util.geo;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.NoSuchAuthorityCodeException;
import org.geotools.api.referencing.crs.CRSAuthorityFactory;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.polygonize.Polygonizer;

/**
 * A helper class to do certain things with JTS.
 */
public class GeoTools {

  /**
   * The WGS'84 coordinate reference system.
   */
  public static final String WGS84_EPSG = "EPSG:4326";
  /**
   * The Google Mercator coordinate reference system, which basically uses meters from -20037508.342789244 to + 20037508.342789244.
   */
  public static final String WEB_MERCATOR_EPSG = "EPSG:3857";

  /**
   * The factory is used to guarantee that the coordinate order is x, y (so longitude/latitude) and not in an unknown state.
   */
  private static final CRSAuthorityFactory factory = CRS.getAuthorityFactory(true);
  private static final ConcurrentHashMap<String, CoordinateReferenceSystem> crsCache = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, MathTransform> transformCache = new ConcurrentHashMap<>();

  /**
   * Returns the cached coordinate reference system for the given EPSG identifier.
   *
   * @param crsId the CRS identifier of the coordinate reference system.
   * @return the coordinate reference system.
   * @throws NullPointerException if the given epsgId is null.
   * @throws NoSuchAuthorityCodeException if the given EPSG identifier is unknown.
   * @throws FactoryException if the requested coordinate reference system can't be created.
   */
  public static CoordinateReferenceSystem crs(final String crsId)
      throws NullPointerException, NoSuchAuthorityCodeException, FactoryException {
    if (crsId == null) {
      throw new NullPointerException("crsId");
    }
    if (crsCache.containsKey(crsId)) {
      return crsCache.get(crsId);
    }
    final CoordinateReferenceSystem newCRS = factory.createCoordinateReferenceSystem(crsId);
    final CoordinateReferenceSystem existingCRS = crsCache.putIfAbsent(crsId, newCRS);
    if (existingCRS != null) {
      return existingCRS;
    }
    return newCRS;
  }

  /**
   * Returns a mathematical transformation from the first given EPSG coordinate reference system into the second one. This method can be
   * used in conjunction with the {@link JTS#transform(Geometry, MathTransform)} method.
   *
   * @param fromCrsId the CRS identifier of the source coordinate reference system.
   * @param toCrsId the CRS identifier of the destination coordinate reference system.
   * @throws NullPointerException if any of the given EPSG identifier is null.
   * @throws NoSuchAuthorityCodeException if any of the given EPSG identifier is unknown.
   * @throws FactoryException if the requested coordinate reference system can't be created or the transformation or the coordinate failed.
   */
  public static MathTransform mathTransform(final String fromCrsId, final String toCrsId)
      throws NullPointerException, NoSuchAuthorityCodeException, FactoryException {
    if (fromCrsId == null) {
      throw new NullPointerException("fromCrsId");
    }
    if (toCrsId == null) {
      throw new NullPointerException("toCrsId");
    }
    final String id = fromCrsId + ":" + toCrsId;
    if (transformCache.containsKey(id)) {
      return transformCache.get(id);
    }
    final CoordinateReferenceSystem fromCRS = crs(fromCrsId);
    final CoordinateReferenceSystem toCRS = crs(toCrsId);
    final MathTransform newTransform = CRS.findMathTransform(fromCRS, toCRS, true);
    final MathTransform existingTransform = transformCache.putIfAbsent(id, newTransform);
    if (existingTransform != null) {
      return existingTransform;
    }
    return newTransform;
  }

  /**
   * Get / create a valid version of the geometry given. If the geometry is a polygon or multi polygon, self intersections / inconsistencies
   * are fixed. Otherwise the geometry is returned.
   *
   * @return a geometry
   */
  public static Geometry validate(Geometry geom) {
    if (geom instanceof Polygon) {
      if (geom.isValid()) {
        geom.normalize(); // validate does not pick up rings in the wrong order - this will fix that
        return geom; // If the polygon is valid just return it
      }
      Polygonizer polygonizer = new Polygonizer();
      addPolygon((Polygon) geom, polygonizer);
      //noinspection unchecked
      return toPolygonGeometry(polygonizer.getPolygons());
    } else if (geom instanceof MultiPolygon) {
      if (geom.isValid()) {
        geom.normalize(); // validate does not pick up rings in the wrong order - this will fix that
        return geom; // If the multipolygon is valid just return it
      }
      Polygonizer polygonizer = new Polygonizer();
      for (int n = geom.getNumGeometries(); n-- > 0; ) {
        addPolygon((Polygon) geom.getGeometryN(n), polygonizer);
      }
      //noinspection unchecked
      return toPolygonGeometry(polygonizer.getPolygons());
    } else {
      return geom; // In my case, I only care about polygon / multipolygon geometries
    }
  }

  /**
   * Add all line strings from the polygon given to the polygonizer given
   *
   * @param polygon polygon from which to extract line strings
   * @param polygonizer polygonizer
   */
  static void addPolygon(Polygon polygon, Polygonizer polygonizer) {
    addLineString(polygon.getExteriorRing(), polygonizer);
    for (int n = polygon.getNumInteriorRing(); n-- > 0; ) {
      addLineString(polygon.getInteriorRingN(n), polygonizer);
    }
  }

  /**
   * Add the linestring given to the polygonizer
   *
   * @param lineString line string
   * @param polygonizer polygonizer
   */
  static void addLineString(LineString lineString, Polygonizer polygonizer) {

    if (lineString instanceof LinearRing) { // LinearRings are treated differently to line strings : we need a LineString NOT a LinearRing
      lineString = lineString.getFactory().createLineString(lineString.getCoordinateSequence());
    }

    // unioning the linestring with the point makes any self intersections explicit.
    Point point = lineString.getFactory().createPoint(lineString.getCoordinateN(0));
    Geometry toAdd = lineString.union(point);

    //Add result to polygonizer
    polygonizer.add(toAdd);
  }

  /**
   * Get a geometry from a collection of polygons.
   *
   * @param polygons collection
   * @return null if there were no polygons, the polygon if there was only one, or a MultiPolygon containing all polygons otherwise
   */
  static Geometry toPolygonGeometry(Collection<Polygon> polygons) {
    switch (polygons.size()) {
      case 0:
        return null; // No valid polygons!
      case 1:
        return polygons.iterator().next(); // single polygon - no need to wrap
      default:
        //polygons may still overlap! Need to sym difference them
        Iterator<Polygon> iter = polygons.iterator();
        Geometry ret = iter.next();
        while (iter.hasNext()) {
          ret = ret.symDifference(iter.next());
        }
        return ret;
    }
  }

  public static boolean geometryCrossesDateline(com.here.xyz.models.geojson.implementation.Geometry geometry, int radius) 
   throws NullPointerException, FactoryException, MismatchedDimensionException, TransformException
  { 

    Geometry degGeo = geometry.getJTSGeometry();
             
    if (radius > 0) {
      MathTransform convertToMeter   = mathTransform("EPSG:4326", "EPSG:31300");
      MathTransform convertFromMeter = mathTransform("EPSG:31300", "EPSG:4326");

      Geometry mtrGeo = JTS.transform( degGeo, convertToMeter );
      mtrGeo = mtrGeo.buffer(radius);
      degGeo = JTS.transform(mtrGeo, convertFromMeter);
    }

    Envelope envelope = degGeo.getEnvelopeInternal();

    boolean r1 = envelope.intersects( 179.999995, envelope.centre().y),
            r2 = envelope.intersects(-179.999995, envelope.centre().y),
            r3 = ( envelope.getMinX() * envelope.getMaxX() ) < 0;
    
    return (r1 || r2) && r3; 
 }

  /**
   * Computes the MathTransform objects required to transform between the given geometry's coordinate reference system (CRS)
   * and the WGS84 CRS.
   *
   * @param geometry the input geometry whose CRS transformations are needed.
   * @return an array of MathTransform objects where:
   *         - the first element transforms from the geometry's CRS to WGS84,
   *         - the second element transforms from WGS84 to the geometry's CRS.
   * @throws FactoryException if there is an error creating the CRS or the MathTransform.
   * @throws javax.xml.crypto.dsig.TransformException if there is an error with CRS transformation.
   */
  private static MathTransform[] getTransforms(Geometry geometry) throws FactoryException {
    String code = "AUTO:42001," + geometry.getCentroid().getCoordinate().x + "," + geometry.getCentroid().getCoordinate().y;
    CoordinateReferenceSystem auto = CRS.decode(code);

    MathTransform fromTransform = CRS.findMathTransform(auto, DefaultGeographicCRS.WGS84);
    MathTransform toTransform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, auto);

    return new MathTransform[] {fromTransform, toTransform};
  }

  /**
   * Applies a buffer operation on a geometry, with the buffer distance specified in meters.
   * The operation uses a projection to approximate the geometry in a Cartesian coordinate system.
   *
   * @param geometry the input geometry to which the buffer is applied.
   * @param distanceInMeters the buffer distance in meters.
   * @return the buffered geometry after transforming back to the original CRS.
   * @throws FactoryException if there is an error creating the CRS or the MathTransform.
   * @throws javax.xml.crypto.dsig.TransformException if there is an error with CRS transformation.
   * @throws TransformException if there is an error applying the coordinate transformation.
   */
  public static Geometry applyBufferInMetersToGeometry(Geometry geometry, double distanceInMeters)
          throws FactoryException, javax.xml.crypto.dsig.TransformException, TransformException {
    MathTransform[] transforms = getTransforms(geometry);
    Geometry pGeom = JTS.transform(geometry, transforms[1]);
    Geometry pBufferedGeom = pGeom.buffer(distanceInMeters);

    return JTS.transform(pBufferedGeom, transforms[0]);
  }

  /**
   * Computes the area of a given geometry in square kilometers.
   * The operation uses a projection to approximate the geometry in a Cartesian coordinate system.
   *
   * @param geometry the input geometry whose area is to be computed.
   * @return the area of the geometry in square kilometers.
   * @throws FactoryException if there is an error creating the CRS or the MathTransform.
   * @throws javax.xml.crypto.dsig.TransformException if there is an error with CRS transformation.
   * @throws TransformException if there is an error applying the coordinate transformation.
   */
  public static double getAreaInSquareKilometersFromGeometry(Geometry geometry)
          throws FactoryException, javax.xml.crypto.dsig.TransformException, TransformException {
    MathTransform[] transforms = getTransforms(geometry);
    Geometry pGeom = JTS.transform(geometry, transforms[1]);
    return pGeom.getArea() / 1_000_000;
  }
}
