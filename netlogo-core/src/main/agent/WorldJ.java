// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.agent;

// We have WorldJ because comeUpForAir is checked in a tight loop and doesn't seem to
// be optimized away by HotSpot
class WorldJ {
  // This is a flag that the engine checks in its tightest innermost loops
  // to see if maybe it should stop running NetLogo code for a moment
  // and do something like halt or update the display.  It doesn't
  // particularly make sense to keep it in World, but since the check
  // occurs in inner loops, we want to put in a place where the engine
  // can get to it very quickly.  And since every Instruction has a
  // World object in it, the engine can always get to World quickly.
  //  - ST 1/10/07
  public volatile boolean comeUpForAir = false;  // NOPMD pmd doesn't like 'volatile'
}
