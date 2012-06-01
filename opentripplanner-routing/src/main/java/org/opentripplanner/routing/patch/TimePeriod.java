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

package org.opentripplanner.routing.patch;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents a period of time, in terms of seconds in [start, end)
 * @author novalis
 *
 */
@XmlType
public class TimePeriod {
    public TimePeriod(long start, long end) {
        this.startTime = start;
        this.endTime = end;
    }

    public TimePeriod() {
    }

    @XmlAttribute
    public long startTime;

    @XmlAttribute
    public long endTime;

    public boolean equals(Object o) {
        if (!(o instanceof TimePeriod)) {
            return false;
        }
        TimePeriod other = (TimePeriod) o;
        return other.startTime == startTime && other.endTime == endTime;
    }

    public int hashCode() {
        return (int) ((startTime & 0x7fff) + (endTime & 0x7fff));
    }
}
