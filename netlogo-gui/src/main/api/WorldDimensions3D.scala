// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.api

import org.nlogo.core.WorldDimensions

/** for wrapping up dimensions to resize the world using WorldResizer */

class WorldDimensions3D(minPxcor: Int, maxPxcor: Int,
                        minPycor: Int, maxPycor: Int,
                        var minPzcor: Int, var maxPzcor: Int,
                        patchSize: Double = 12.0,
                        wrappingAllowedInX: Boolean = true,
                        wrappingAllowedInY: Boolean = true,
                        val wrappingAllowedInZ: Boolean = true)
extends WorldDimensions(minPxcor, maxPxcor, minPycor, maxPycor, patchSize, wrappingAllowedInX, wrappingAllowedInY) {
  def copy(minPxcor: Int, maxPxcor: Int, minPycor: Int, maxPycor: Int, minPzcor: Int, maxPzcor: Int): WorldDimensions3D = {
    new WorldDimensions3D(minPxcor, maxPxcor, minPycor, maxPycor, minPzcor, maxPzcor)
  }

  // can't define default arguments in more than one method of the same name
  // and that's done in core.WorldDimensions
  def copyThreeD(
    minPxcor: Int = minPxcor,
    maxPxcor: Int = maxPxcor,
    minPycor: Int = minPycor,
    maxPycor: Int = maxPycor,
    minPzcor: Int = minPzcor,
    maxPzcor: Int = maxPzcor,
    patchSize: Double = patchSize,
    wrappingAllowedInX: Boolean = wrappingAllowedInX,
    wrappingAllowedInY: Boolean = wrappingAllowedInY,
    wrappingAllowedInZ: Boolean = wrappingAllowedInZ): WorldDimensions3D = {
    new WorldDimensions3D(minPxcor, maxPxcor, minPycor, maxPycor, minPzcor, maxPzcor, patchSize, wrappingAllowedInX, wrappingAllowedInY, wrappingAllowedInZ)
  }
}
