// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.nvm

import org.nlogo.core.{ ClosedVariable, Let, StructureDeclarations, Token, prim => coreprim }

final class LiftedLambda(
  name:               String,
  nameToken:          Token,
  argTokens:          Seq[Token],
  val parent:         Procedure,
  val lambdaFormals:  Seq[Let],
  val closedLets:     Set[Let]
  ) extends Procedure(false, name, nameToken, argTokens, null) {

    args = Vector[String]()

    val lambdaFormalsArray: Array[Let] = lambdaFormals.toArray[Let]

    override val isLambda = true

    def getLambdaFormal(name: String): Option[Let] =
      lambdaFormals.find(_.name == name) orElse (parent match {
        case parentLambda: LiftedLambda => parentLambda.getLambdaFormal(name)
        case _ => None
      })

    override def dump: String = {
      val buf = new StringBuilder
      var displayArgs = args.mkString("[", " ", "]")
      val titleMargin = if (isReporter) "   reporter " else "   "
      buf ++= s"$titleMargin$displayName:${parent.displayName}${displayArgs}{$agentClassString}:\n"
      for (i <- code.indices) {
        buf ++= s"   [$i]${code(i).dump(6)}\n"
      }
      for (p <- children) {
        buf ++= "\n"
        buf ++= p.dump
      }
      buf.toString
    }
}
