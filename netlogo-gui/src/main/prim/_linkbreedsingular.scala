// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim

import org.nlogo.agent.{ AgentSet, Link }
import org.nlogo.api.{ LogoException}
import org.nlogo.core.Syntax
import org.nlogo.core.Nobody
import org.nlogo.nvm.{ Context, Reporter }

class _linkbreedsingular(breedName: String) extends Reporter {


  override def toString: String = s"${super.toString}:$breedName"

  override def report(context: Context): AnyRef = {
    val breed = world.getLinkBreed(breedName)
    val link = world.getLink(argEvalDouble(context, 0), argEvalDouble(context, 1), breed)
    if (link == null) Nobody else link
  }
}
