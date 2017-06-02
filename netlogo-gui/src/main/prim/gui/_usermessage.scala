// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.gui

import org.nlogo.api.{ Dump, ReporterRunnable}
import org.nlogo.core.Syntax
import org.nlogo.core.I18N
import org.nlogo.nvm.{ Command, Context }
import org.nlogo.swing.OptionDialog
import org.nlogo.window.GUIWorkspace
import org.nlogo.internalapi.ShowMessage

class _usermessage extends Command {



  override def perform(context: Context) {
    val message = Dump.logoObject(args(0).report(context))
    workspace match {
      case gw: GUIWorkspace =>
        gw.updateUI()
        val canceled = workspace.waitForResult(
          new ReporterRunnable[java.lang.Boolean] {
            override def run = {
              gw.view.mouseDown(false)
              Boolean.box(1 ==
                OptionDialog.showMessage(gw.getFrame, "User Message", message,
                                  Array(I18N.gui.get("common.buttons.ok"),
                                        I18N.gui.get("common.buttons.halt"))))
            }}).booleanValue
        if(canceled)
          throw new org.nlogo.nvm.HaltException(true)
        context.ip = next
      case _: org.nlogo.internalapi.WritableGUIWorkspace =>
        // this is here to differentiate JavaFX from Swing
        context.job.displayUI(ShowMessage(message), context, next)
      case _ =>
        // if not in GUI, just do nothing
        context.ip = next
    }
  }

}
