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
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.graph.AbstractEdge;
import org.opentripplanner.routing.vertextype.StreetVertex;

/**
 *  Abstract base class for edges in the (open)streetmap layer 
 *  (might be paths, stairs, etc. as well as streets).  This
 *  can be used as a marker to detect edges in the street layer.
 */

/* package-private ? */ 
public abstract class StreetEdge extends AbstractEdge implements EdgeWithElevation {
    
    private static final long serialVersionUID = 1L;

    public StreetEdge(StreetVertex v1, StreetVertex v2) {
        super(v1, v2);
    }
    
    public abstract boolean canTraverse(RoutingRequest options);
    
    public abstract double getLength();
    
    public abstract StreetTraversalPermission getPermission();
    
    public abstract boolean isNoThruTraffic();
    
}
