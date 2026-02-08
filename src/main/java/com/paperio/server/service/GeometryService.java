package com.paperio.server.service;

import jakarta.annotation.PostConstruct;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeometryService {
    private final GeometryFactory factory = new GeometryFactory();

    @PostConstruct
    public void warmUp() {
        Geometry circle = createInitialCircle(0, 0, 50);
        Geometry square = factory.createPolygon(new Coordinate[]{
                new Coordinate(0,0), new Coordinate(10,0),
                new Coordinate(10,10), new Coordinate(0,10), new Coordinate(0,0)
        });

        for (int i = 0; i < 500; i++) {
            circle.union(square);
            circle.difference(square);
            TopologyPreservingSimplifier.simplify(circle, 1.0);
        }
    }

    public Geometry createInitialCircle(double x, double y, double radius) {
        return factory.createPoint(new Coordinate(x, y)).buffer(radius);
    }

    public Geometry conquer(Geometry currentTerritory, List<Coordinate> trail) {
        if (trail.size() < 3) return currentTerritory;
        try {
            Coordinate[] coords = trail.toArray(new Coordinate[0]);
            LineString trailLine = factory.createLineString(coords);

            Geometry thickenedTrail = trailLine.buffer(3.0);

            Geometry combined = currentTerritory.union(thickenedTrail);

            Geometry repaired = combined.buffer(5.0).buffer(-5.0);

            Geometry filled = fillAllGaps(repaired);

            return TopologyPreservingSimplifier.simplify(filled, 1.0).buffer(0);
        } catch (Exception e) {
            return currentTerritory;
        }
    }

    private Geometry fillAllGaps(Geometry geom) {
        if (geom.isEmpty()) return geom;

        List<Polygon> solids = new ArrayList<>();
        for (int i = 0; i < geom.getNumGeometries(); i++) {
            if (geom.getGeometryN(i) instanceof Polygon p) {
                solids.add(factory.createPolygon(p.getExteriorRing().getCoordinates()));
            }
        }

        if (solids.isEmpty()) return geom;
        return solids.size() == 1 ? solids.getFirst() : factory.createMultiPolygon(solids.toArray(new Polygon[0]));
    }

    public LineString createLine(List<Coordinate> points) {
        if (points.size() < 2) return null;
        return factory.createLineString(points.toArray(new Coordinate[0]));
    }
}