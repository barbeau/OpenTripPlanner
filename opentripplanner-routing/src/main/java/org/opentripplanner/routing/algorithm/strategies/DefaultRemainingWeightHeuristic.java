/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (props, at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.algorithm.strategies;

import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.graph.Vertex;

/**
 * A euclidian remaining weight strategy that takes into account transit 
 * boarding costs where applicable.
 *
 */
public class DefaultRemainingWeightHeuristic implements RemainingWeightHeuristic {

    private static final long serialVersionUID = -5172878150967231550L;

<<<<<<< HEAD
    private TraverseOptions options;
=======
    private RoutingRequest options;
>>>>>>> OpenPlans-remote/master

    private boolean useTransit = false;

    private double maxSpeed;

    @Override
    public double computeInitialWeight(State s, Vertex target) {
        this.options = s.getOptions();
        this.useTransit = options.getModes().isTransit();
        this.maxSpeed = getMaxSpeed(options);
        return s.getVertex().fastDistance(target) / maxSpeed;
    }

    @Override
    public double computeForwardWeight(State s, Vertex target) {

    	Vertex sv = s.getVertex();
        double euclidianDistance = sv.fastDistance(target);
        /*	On a non-transit trip, the remaining weight is simply distance / speed
         *	On a transit trip, there are two cases:
         *	(1) we're not on a transit vehicle.  In this case, there are two possible ways to 
         *      compute the remaining distance, and we take whichever is smaller: 
         *	    (a) walking distance / walking speed
         *	    (b) boarding cost + transit distance / transit speed (this is complicated a 
         *          bit when we know that there is some walking portion of the trip).
         *	(2) we are on a transit vehicle, in which case the remaining weight is
         *	    simply transit distance / transit speed (no need for boarding cost),
         *	    again considering any mandatory walking.
         */
        if (useTransit) {
            double speed = options.getSpeedUpperBound();
            if (s.isAlightedLocal()) {
                if (euclidianDistance + s.getWalkDistance() > options.getMaxWalkDistance()) {
                    return -1;
                }
<<<<<<< HEAD
                return options.walkReluctance * euclidianDistance / options.speed;
=======
                return options.walkReluctance * euclidianDistance / speed;
>>>>>>> OpenPlans-remote/master
            } else {
                int boardCost;
                if (s.isOnboard()) {
                    boardCost = 0;
                } else {
                    boardCost = options.getBoardCostLowerBound();
                }
                if (s.isEverBoarded()) {
                    boardCost += options.transferPenalty;
                }
                if (euclidianDistance < target.getDistanceToNearestTransitStop()) {
                    if (euclidianDistance + s.getWalkDistance() > options.getMaxWalkDistance()) {
                        return -1;
                    }
<<<<<<< HEAD
                    return options.walkReluctance * euclidianDistance / options.speed;
=======
                    return options.walkReluctance * euclidianDistance / speed;
>>>>>>> OpenPlans-remote/master
                } else {
                    double mandatoryWalkDistance = target.getDistanceToNearestTransitStop()
                            + sv.getDistanceToNearestTransitStop();
                    if (mandatoryWalkDistance + s.getWalkDistance() > options.getMaxWalkDistance()) {
                        return -1;
                    }
                    double distance = (euclidianDistance - mandatoryWalkDistance) / maxSpeed
                            + mandatoryWalkDistance * options.walkReluctance / speed
                            + boardCost;
                    return Math.min(distance, options.walkReluctance * euclidianDistance
                            / speed);
                }
            }
        }
        else {
            // This was missing from the original AStar, but it only makes sense
            return options.walkReluctance * euclidianDistance / maxSpeed;
        }
    }

    @Override
    public double computeReverseWeight(State s, Vertex target) {
    	// from and to are interpreted in the direction of traversal
    	// so the edge actually leads from

    	Vertex sv = s.getVertex();
        
        double euclidianDistance = sv.fastDistance(target);

        if (useTransit) {
            double speed = options.getSpeedUpperBound();
            if (s.isAlightedLocal()) {
                if (euclidianDistance + s.getWalkDistance() > options.getMaxWalkDistance()) {
                    return -1;
                }
<<<<<<< HEAD
                return options.walkReluctance * euclidianDistance / options.speed;
=======
                return options.walkReluctance * euclidianDistance / speed;
>>>>>>> OpenPlans-remote/master
            } else {
                int boardCost;
                if (s.isOnboard()) {
                    boardCost = 0;
                } else {
                    boardCost = options.getBoardCostLowerBound();
                }
                if (s.isEverBoarded()) {
                    boardCost += options.transferPenalty;
                }
                if (euclidianDistance < target.getDistanceToNearestTransitStop()) {
                    if (euclidianDistance + s.getWalkDistance() > options.getMaxWalkDistance()) {
                        return -1;
                    }
                    return options.walkReluctance * euclidianDistance
                            / speed;
                } else {
                    double mandatoryWalkDistance = target
                            .getDistanceToNearestTransitStop()
                            + sv.getDistanceToNearestTransitStop();
                    if (mandatoryWalkDistance + s.getWalkDistance() > options.getMaxWalkDistance()) {
                        return -1;
                    }
                    double distance = (euclidianDistance - mandatoryWalkDistance) / maxSpeed
                            + mandatoryWalkDistance * options.walkReluctance
                            / speed + boardCost;
                    return Math.min(distance, options.walkReluctance
                            * euclidianDistance / speed);
                }
            }
        } else {
            return options.walkReluctance * euclidianDistance / maxSpeed;
        }
    }
    

    public static double getMaxSpeed(RoutingRequest options) {
        if (options.getModes().contains(TraverseMode.TRANSIT)) {
            // assume that the max average transit speed over a hop is 10 m/s, which is roughly
            // true in Portland and NYC, but *not* true on highways
            return 10;
        } else {
            if (options.optimize == OptimizeType.QUICK) {
                return options.getSpeedUpperBound();
            } else {
                // assume that the best route is no more than 10 times better than
                // the as-the-crow-flies flat base route.
                return options.getSpeedUpperBound() * 10;
            }
        }
    }

	@Override
	public void reset() {
	}
}
