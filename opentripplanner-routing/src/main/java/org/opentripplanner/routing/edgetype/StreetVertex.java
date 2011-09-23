/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.edgetype;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opentripplanner.common.geometry.DirectionUtils;
import org.opentripplanner.common.geometry.PackedCoordinateSequence;
import org.opentripplanner.routing.core.GenericVertex;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.patch.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

/**
 * This vertex represents one direction of a street. Its location is the start of that street in
 * that direction.  It contains most of the data used for edges from the street.
 * 
 */
public class StreetVertex extends GenericVertex {

    private static Logger log = LoggerFactory.getLogger(StreetVertex.class);

    /* These numbers disagree with everything else I (David Turner) have read about the energy cost
     of cycling but given that we are going to be fudging them anyway, they're not totally crazy */ 
    private static final double JOULES_PER_METER_ON_FLAT = 6.8;
    private static final double JOULES_SLOPE_FACTOR = 73.7;
    
    private static final long serialVersionUID = -385126804908021091L;

    /**
     * This declares that greenways are streets that are more than 10x as safe as
     * ordinary streets.
     */
	public static final double GREENWAY_SAFETY_FACTOR = 0.1;

    /** 
     * Adjustment to make work costs comparable to time and safety costs
     */
    public  static final double WORK_NORMALIZATION_FACTOR = 0.0294;
    
    /** 
     * Default speed used to compute work costs 
     */
    public static final double DEFAULT_BICYCLE_SPEED = 5;
    
    /**
     * each additional m/s of speed over the default makes each meter this much worse.
     * The actual number is closer to 2.0 on the speed range (5,10), but we definitely
     * never want to go negative, and this number ensures that.
     */
    public static final double SPEED_OVERHEAD = JOULES_PER_METER_ON_FLAT/DEFAULT_BICYCLE_SPEED;
    
    public LineString geometry;

    protected boolean wheelchairAccessible = true;

    protected double maxSlope;

    protected PackedCoordinateSequence elevationProfile;

    protected boolean slopeOverride;

    protected double slopeSpeedEffectiveLength;

    protected double slopeWorkCost;

    protected StreetTraversalPermission permission;

    double length;

    public int inAngle;

    public int outAngle;

    /** is this street a staircase */
    private boolean stairs = false;

    protected boolean crossable = true; //can this street be safely crossed? (unused)
    
    protected double bicycleSafetyEffectiveLength;

    protected String edgeId;

    protected static Coordinate getCoord(LineString geometry) {
        return geometry.getCoordinateN(0);
    }

    private boolean roundabout = false;

    private Set<Alert> notes;

    private boolean hasBogusName;

    private boolean noThruTraffic = false;

    public StreetVertex(String id, LineString geometry, String name, double length, boolean back, Set<Alert> notes) {
        super(id + (back ? " back" : ""), getCoord(geometry), name);
        this.edgeId = id;
        this.geometry = geometry;
        this.length = length;
        this.bicycleSafetyEffectiveLength = length;
        this.slopeWorkCost = length;
        this.slopeSpeedEffectiveLength = length;
        this.name = name;
        this.permission = StreetTraversalPermission.ALL;
        this.notes = notes;
        
        if (geometry != null) {
            double angleR = DirectionUtils.getLastAngle(geometry);
            outAngle = ((int) (180 * angleR / Math.PI) + 180 + 360) % 360;
            angleR = DirectionUtils.getFirstAngle(geometry);
            inAngle = ((int) (180 * angleR / Math.PI) + 180 + 360) % 360;
        }
    }

    public void setGeometry(LineString g) {
        geometry = g;
    }

    public PackedCoordinateSequence getElevationProfile() {
        return elevationProfile;
    }

    /**
     * Returns a subset of the elevation profile given a range. The x-values of the returned
     * coordinates are adjusted accordingly when the start value is greater than 0.
     * 
     * @param start
     * @param end
     * @return a PackedCoordinateSequence
     */
    public PackedCoordinateSequence getElevationProfile(double start, double end) {
        if (elevationProfile == null)
            return null;
        List<Coordinate> coordList = new LinkedList<Coordinate>();

        if (start < 0)
            start = 0;
        if (end > length)
            end = length;

        for (Coordinate coord : elevationProfile.toCoordinateArray()) {
            if (coord.x >= start && coord.x <= end) {
                coordList.add(new Coordinate(coord.x - start, coord.y));
            }
        }

        Coordinate coordArr[] = new Coordinate[coordList.size()];
        return new PackedCoordinateSequence.Float(coordList.toArray(coordArr), 2);
    }

    public void setElevationProfile(PackedCoordinateSequence elev) {
        if (elev == null) {
            return;
        }
        if (slopeOverride) {
            elev = new PackedCoordinateSequence.Float(new Coordinate[] { elev.getCoordinate(0),elev.getCoordinate((elev.size()-1))},
                    2);
        }
        elevationProfile = elev;
        // compute the cost of the elevation changes
        SlopeCosts costs= StreetVertex.computeSlopeCost(elev, getName());
        slopeSpeedEffectiveLength = costs.slopeSpeedEffectiveLength;
        maxSlope = costs.maxSlope;
        slopeWorkCost = costs.slopeWorkCost;
    }

    public static SlopeCosts computeSlopeCost(PackedCoordinateSequence elev, String name) {
        Coordinate[] coordinates = elev.toCoordinateArray();
        
        double maxSlope = 0;
        double slopeSpeedEffectiveLength = 0;
        double slopeWorkCost = 0;
        for (int i = 0; i < coordinates.length - 1; ++i) {
            double run = coordinates[i + 1].x - coordinates[i].x;
            double rise = coordinates[i + 1].y - coordinates[i].y;
            double slope = rise / run;
            if (slope > 0.35 || slope < -0.35) {
                slope = 0; // Baldwin St in Dunedin, NZ, is the steepest street on earth, and has a
                // grade of 35%. So, this must be a data error.
                log
                        .warn("Warning: street "
                                + name
                                + " steeper than Baldwin Street.  This is an error in the algorithm or the data");
            }
            if (maxSlope < Math.abs(slope)) {
                maxSlope = Math.abs(slope);
            }
            /*
             * This is the cost in joules holding speed fixed at 5 m/s, roughly, computed with
             * data from analyticcycling.com. For faster speeds, the Y-intercept is higher, but the
             * slope (of the road-slope vs joule-per-meter line) is nearly the same. This implies
             * that it bottoms out lower (as for technical reasons the cost can never be less than
             * zero).  
             * 
             * This is moot, because we want a fairly high minimum cost, since the theoretical
             * numbers do not really agree with cyclist preferences.  
             */

            double slope_or_zero = Math.max(slope, 0);
            double joules = run * (JOULES_PER_METER_ON_FLAT + JOULES_SLOPE_FACTOR * slope_or_zero);
            slopeWorkCost += joules * WORK_NORMALIZATION_FACTOR;
            slopeSpeedEffectiveLength += run * slopeSpeedCoefficient(slope, coordinates[i].y);
        }
        return new SlopeCosts(slopeSpeedEffectiveLength, slopeWorkCost, maxSlope); 
    }

    public static double slopeSpeedCoefficient(double slope, double altitude) {
        /*
         * computed by asking ZunZun for a quadratic b-spline approximating some values from
         * http://www.analyticcycling.com/ForcesSpeed_Page.html fixme: should clamp to local speed
         * limits (code is from ZunZun)
         */
        double tx[] = { 0.0000000000000000E+00, 0.0000000000000000E+00, 0.0000000000000000E+00,
                2.7987785324442748E+03, 5.0000000000000000E+03, 5.0000000000000000E+03,
                5.0000000000000000E+03 };
        double ty[] = { -3.4999999999999998E-01, -3.4999999999999998E-01, -3.4999999999999998E-01,
                -7.2695627831828688E-02, -2.4945814335295903E-03, 5.3500304527448035E-02,
                1.2191105175593375E-01, 3.4999999999999998E-01, 3.4999999999999998E-01,
                3.4999999999999998E-01 };
        double coeff[] = { 4.3843513168660255E+00, 3.6904323727375652E+00, 1.6791850199667697E+00,
                5.5077866957024113E-01, 1.7977766419113900E-01, 8.0906832222762959E-02,
                6.0239305785343762E-02, 4.6782343053423814E+00, 3.9250580214736304E+00,
                1.7924585866601270E+00, 5.3426170441723031E-01, 1.8787442260720733E-01,
                7.4706427576152687E-02, 6.2201805553147201E-02, 5.3131908923568787E+00,
                4.4703901299120750E+00, 2.0085381385545351E+00, 5.4611063530784010E-01,
                1.8034042959223889E-01, 8.1456939988273691E-02, 5.9806795955995307E-02,
                5.6384893192212662E+00, 4.7732222200176633E+00, 2.1021485412233019E+00,
                5.7862890496126462E-01, 1.6358571778476885E-01, 9.4846184210137130E-02,
                5.5464612133430242E-02 };
        int nx = 7;
        int ny = 10;
        int kx = 2;
        int ky = 2;

        double h[] = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
        double hh[] = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
        double w_x[] = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
        double w_y[] = { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };

        int i, j, li, lj, lx, ky1, nky1, ly, i1, j1, l2;
        double f, temp;

        int kx1 = kx + 1;
        int nkx1 = nx - kx1;
        int l = kx1;
        int l1 = l + 1;

        while ((altitude >= tx[l1 - 1]) && (l != nkx1)) {
            l = l1;
            l1 = l + 1;
        }

        h[0] = 1.0;
        for (j = 1; j < kx + 1; j++) {
            for (i = 0; i < j; i++) {
                hh[i] = h[i];
            }
            h[0] = 0.0;
            for (i = 0; i < j; i++) {
                li = l + i;
                lj = li - j;
                if (tx[li] != tx[lj]) {
                    f = hh[i] / (tx[li] - tx[lj]);
                    h[i] = h[i] + f * (tx[li] - altitude);
                    h[i + 1] = f * (altitude - tx[lj]);
                } else {
                    h[i + 1 - 1] = 0.0;
                }
            }
        }

        lx = l - kx1;
        for (j = 0; j < kx1; j++) {
            w_x[j] = h[j];
        }

        ky1 = ky + 1;
        nky1 = ny - ky1;
        l = ky1;
        l1 = l + 1;

        while ((slope >= ty[l1 - 1]) && (l != nky1)) {
            l = l1;
            l1 = l + 1;
        }

        h[0] = 1.0;
        for (j = 1; j < ky + 1; j++) {
            for (i = 0; i < j; i++) {
                hh[i] = h[i];
            }
            h[0] = 0.0;
            for (i = 0; i < j; i++) {
                li = l + i;
                lj = li - j;
                if (ty[li] != ty[lj]) {
                    f = hh[i] / (ty[li] - ty[lj]);
                    h[i] = h[i] + f * (ty[li] - slope);
                    h[i + 1] = f * (slope - ty[lj]);
                } else {
                    h[i + 1 - 1] = 0.0;
                }
            }
        }

        ly = l - ky1;
        for (j = 0; j < ky1; j++) {
            w_y[j] = h[j];
        }

        l = lx * nky1;
        for (i1 = 0; i1 < kx1; i1++) {
            h[i1] = w_x[i1];
        }

        l1 = l + ly;
        temp = 0.0;
        for (i1 = 0; i1 < kx1; i1++) {
            l2 = l1;
            for (j1 = 0; j1 < ky1; j1++) {
                l2 = l2 + 1;
                temp = temp + coeff[l2 - 1] * h[i1] * w_y[j1];
            }
            l1 = l1 + nky1;
        }

        return temp;
    }

    public boolean canTraverse(TraverseOptions wo) {
        if (wo.wheelchairAccessible) {
            if (!wheelchairAccessible) {
                return false;
            }
            if (maxSlope > wo.maxSlope) {
                return false;
            }
        }

        if (wo.getModes().getWalk() && permission.allows(StreetTraversalPermission.PEDESTRIAN)) {
            return true;
        }

        if (wo.getModes().getBicycle() && permission.allows(StreetTraversalPermission.BICYCLE)) {
            return true;
        }

        if (wo.getModes().getCar() && permission.allows(StreetTraversalPermission.CAR)) {
            return true;
        }

        return false;
    }

    public double computeWeight(State s0, TraverseOptions options, double time) {
        double weight;
        if (options.wheelchairAccessible) {
            // in fact, a wheelchair user will probably be going slower
            // than a cyclist, having less wind resistance, but will have
            // a stronger preference for less work. Maybe it
            // evens out?
            weight = slopeSpeedEffectiveLength / options.speed;
        } else if (options.getModes().contains(TraverseMode.BICYCLE)) {
            switch (options.optimizeFor) {
            case SAFE:
            	weight = bicycleSafetyEffectiveLength / options.speed;
            	break;
            case GREENWAYS:
                weight = bicycleSafetyEffectiveLength / options.speed;
                if (bicycleSafetyEffectiveLength / length <= GREENWAY_SAFETY_FACTOR) {
                	//greenways are treated as even safer than they really are
                	weight *= 0.66;
                }
                break;
            case FLAT:
                /*
                 * We do not use the speed overhead (that is, how much harder a cyclist has to work
                 * to overcome wind resistance and friction at high speeds) because while it more
                 * accurately models the cost of biking on slopes, it makes it harder to compare
                 * costs between transit and biking
                 * 
                 * double speedOverhead = SPEED_OVERHEAD * WORK_NORMALIZATION_FACTOR * length
                 * (options.speed - DEFAULT_BICYCLE_SPEED);
                 */
                weight = length / options.speed + slopeWorkCost;
                break;
            case QUICK:
                weight = slopeSpeedEffectiveLength / options.speed;
                break;
            case TRIANGLE:
                double quick = slopeSpeedEffectiveLength / options.speed;
                double safety = bicycleSafetyEffectiveLength / options.speed;
                /* See note under case FLAT
                 * speedOverhead = SPEED_OVERHEAD * WORK_NORMALIZATION_FACTOR * length
                 *      * (options.speed - DEFAULT_BICYCLE_SPEED);
                 */
                double slope = slopeWorkCost;
                weight = quick * options.triangleTimeFactor + slope * options.triangleSlopeFactor + safety * options.triangleSafetyFactor;
                break;
            default:
                weight = length / options.speed;
            }
        } else {
            weight = time;
        }
        if (stairs) {
        	weight *= options.stairsReluctance;
        } else {
        	weight *= options.walkReluctance;
        }
        return weight;
    }

    public void setSlopeOverride(boolean slopeOverride) {
        this.slopeOverride = slopeOverride;
    }

    public void setBicycleSafetyEffectiveLength(double bicycleSafetyEffectiveLength) {
        this.bicycleSafetyEffectiveLength = bicycleSafetyEffectiveLength;
    }

    public String toString() {
        return "<" + label + " (" + name + ")>";
    }

    public String getEdgeId() {
        return edgeId;
    }

    public LineString getGeometry() {
        return geometry;
    }

    public double getLength() {
        return length;
    }

    public StreetTraversalPermission getPermission() {
        return permission;
    }

    public double getBicycleSafetyEffectiveLength() {
        return bicycleSafetyEffectiveLength;
    }

    public void setPermission(StreetTraversalPermission permission) {
        this.permission = permission;
    }

    public void setWheelchairAccessible(boolean wheelchairAccessible) {
        this.wheelchairAccessible = wheelchairAccessible;
    }

    public boolean isWheelchairAccessible() {
        return wheelchairAccessible;
    }
    
    public void setCrossable(boolean crossable) {
        this.crossable = crossable; 
    }
    
    public boolean isCrossable() {
        return crossable;
    }

    public void setRoundabout(boolean roundabout) {
        this.roundabout = roundabout;
    }

    public boolean isRoundabout() {
        return roundabout;
    }

    public Set<Alert> getNotes() {
        return notes;
    }

	public boolean hasBogusName() {
		return hasBogusName;
	}

	public void setBogusName(boolean hasBogusName) {
		this.hasBogusName = hasBogusName;
	}

    public void setNoThruTraffic(boolean noThruTraffic) {
        this.noThruTraffic = noThruTraffic;
    }

    public boolean isNoThruTraffic() {
        return noThruTraffic;
    }

	public void setStairs(boolean stairs) {
		this.stairs = stairs;
	}

	public boolean isStairs() {
		return stairs;
	}
}
