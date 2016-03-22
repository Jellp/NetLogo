// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.app

import org.nlogo.core.AgentKind
import org.nlogo.core.I18N
import org.nlogo.api.Editable
import org.nlogo.api.ModelSection
import org.nlogo.api.Version
import org.nlogo.api.VersionHistory
import org.nlogo.awt.Fonts
import org.nlogo.awt.Images
import org.nlogo.awt.Hierarchy
import org.nlogo.awt.UserCancelException
import org.nlogo.log.Logger
import org.nlogo.swing.{ FileDialog => SwingFileDialog }
import org.nlogo.swing.ModalProgressTask
import org.nlogo.api.{ Exceptions => ApiExceptions }
import org.nlogo.window.GUIWorkspace
import org.nlogo.window.CodeEditor
import org.nlogo.window.EditorColorizer
import org.nlogo.window.Events.EditWidgetEvent
import org.nlogo.window.Events.CompileAllEvent
import org.nlogo.window.Events.CompileMoreSourceEvent
import org.nlogo.window.Events.LoadSectionEvent
import org.nlogo.window.Events.RemoveConstraintEvent
import org.nlogo.window.ViewWidgetInterface
import org.nlogo.window.Widget
import org.nlogo.window.WidgetRegistry
import org.nlogo.window.ButtonWidget
import org.nlogo.window.ChooserWidget
import org.nlogo.window.InputBoxWidget
import org.nlogo.window.InterfaceGlobalWidget
import org.nlogo.window.JobWidget
import org.nlogo.window.MonitorWidget
import org.nlogo.window.OutputWidget
import org.nlogo.window.PlotWidget
import org.nlogo.window.SliderWidget
import org.nlogo.window.ViewWidget
import org.nlogo.workspace.Evaluator

import javax.imageio.ImageIO
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JOptionPane
import java.awt.Cursor
import java.awt.Font
import java.awt.{ FileDialog => AwtFileDialog }
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent
import java.io.IOException
import java.util.ArrayList
import java.util.{ List => JList }

import scala.collection.JavaConverters._

class InterfacePanel(val viewWidget: ViewWidgetInterface, workspace: GUIWorkspace)
  extends WidgetPanel(workspace)
  with java.awt.event.KeyListener
  with LoadSectionEvent.Handler
  with org.nlogo.window.Events.ExportInterfaceEvent.Handler {

  workspace.setWidgetContainer(this)
  // in 3d don't add the view widget since it's always
  // disabled there's no reason for it to take space 7/5/07
  if (!Version.is3D)
    addWidget(viewWidget.asInstanceOf[Widget], 0, 0, false, false)

  viewWidget.asInstanceOf[Widget].deleteable = false
  addKeyListener(this)
  addMouseListener(this)

  ///

  override def focusGained(e: FocusEvent): Unit = {
    _hasFocus = true
    enableButtonKeys(true)
  }

  override def focusLost(e: FocusEvent): Unit = {
    _hasFocus = false
    enableButtonKeys(false)
  }

  ///

  override protected def doPopup(e: MouseEvent): Unit = {
    val menu = new JPopupMenu()
    Seq("button", "slider", "switch", "chooser", "input", "monitor", "plot").foreach { widgetKind =>
      menu.add(new WidgetCreationMenuItem(I18N.gui.get(s"tabs.run.widgets.$widgetKind"), widgetKind.toUpperCase, e.getX, e.getY))
    }

    // add all the widgets
    val outputItem =
        new WidgetCreationMenuItem(I18N.gui.get("tabs.run.widgets.output"), "OUTPUT", e.getX, e.getY)
    if (getOutputWidget != null) {
      outputItem.setEnabled(false)
    }
    menu.add(outputItem)

    menu.add(new WidgetCreationMenuItem(I18N.gui.get("tabs.run.widgets.note"), "NOTE", e.getX, e.getY))

    // add extra stuff
    menu.add(new JPopupMenu.Separator())
    menu.add(exportItem)

    menu.show(this, e.getX, e.getY)
  }

  val exportItem: JMenuItem = {
    val exportAction = new ActionListener() {
      def actionPerformed(e: ActionEvent): Unit = {
        try {
          exportInterface()
        } catch  {
          case ex: IOException =>
            JOptionPane.showMessageDialog(InterfacePanel.this, ex.getMessage(),
              I18N.gui.get("common.messages.error"), JOptionPane.ERROR_MESSAGE);
        }
      }
    }
    val exportItem = new JMenuItem(
      I18N.gui.get("menu.file.export.interface"))
    exportItem.addActionListener(exportAction)
    exportItem
  }

  class WidgetCreationMenuItem(val displayName: String, val widgetType: String, x: Int, y: Int)
  extends JMenuItem(displayName) {
    val listener = new ActionListener() {
      override def actionPerformed(e: ActionEvent): Unit = {
        val widget = makeWidget(widgetType, false)
        val wrapper = addWidget(widget, x, y, true, false)
        revalidate()
        wrapper.selected(true)
        wrapper.foreground()
        wrapper.isNew(true)
        new EditWidgetEvent(null).raise(InterfacePanel.this)
        newWidget.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
        wrapper.isNew(false)
        newWidget = null
      }
    }
    addActionListener(listener)
  }

  // This is used both when loading a model and when the user is making
  // new widgets in the UI.  For most widget types, the same type string
  // is used in both places. - ST 3/17/04
  override def makeWidget(lowerCaseType: String, loading: Boolean): Widget = {
    val tpe = lowerCaseType.toUpperCase
    val fromRegistry = WidgetRegistry(tpe)
    if (fromRegistry != null)
      fromRegistry
    else if (tpe.equalsIgnoreCase("SLIDER")) {
      new SliderWidget(workspace.world.auxRNG) {
        override def sourceOffset: Int =
          Evaluator.sourceOffset(AgentKind.Observer, false)
      }
    } else if (tpe.equals("CHOOSER") || // current name
        tpe.equals("CHOICE"))   // old name, used in old models
      new org.nlogo.window.ChooserWidget(workspace)
    else if (tpe.equals("BUTTON"))
      new ButtonWidget(workspace.world.mainRNG)
    else if (tpe.equals("PLOT"))
      PlotWidget(workspace.plotManager)
    else if (tpe.equals("MONITOR"))
      new MonitorWidget(workspace.world.auxRNG)
    else if (tpe.equals("INPUT") ||  // in the GUI, it's "Input Box"
        tpe.equals("INPUTBOX"))  // in saved models, it's "INPUTBOX"
    {
      val font = new Font(Fonts.platformMonospacedFont,
        Font.PLAIN, 12)
      val textArea = new CodeEditor(1, 20, font, false, null, new EditorColorizer(workspace), I18N.gui.fn)
      val dialogTextArea = new CodeEditor(5, 20, font, true, null, new EditorColorizer(workspace), I18N.gui.fn)
      new InputBoxWidget(textArea, dialogTextArea, workspace, this)
    } else if (tpe.equals("OUTPUT"))  // currently in saved models only - ST 3/17/04
      new OutputWidget()
    else
      throw new IllegalStateException("unknown widget type: " + tpe)
  }

  override private[app] def deleteWidgets(hitList: Seq[WidgetWrapper]): Unit = {
    var needsRecompile: Boolean = false
    for (wrapper <- hitList) {
      removeWidget(wrapper)
      wrapper.widget match {
        // this ensures that the right thing happens if we delete
        // a button or monitor that doesn't compile; we need to remove it
        // from the errors tab - ST 12/17/04
        case jobWidget: JobWidget =>
          jobWidget.innerSource("")
          new CompileMoreSourceEvent(jobWidget).raise(this)
        case _ =>
      }
      wrapper.widget match {
        case _: InterfaceGlobalWidget => needsRecompile = true
        case _ =>
      }
    }
    setForegroundWrapper()
    revalidate()
    repaint() // you wouldn't think this'd be necessary, but without it
    // the widget didn't visually disappear - ST 6/23/03
    if (needsRecompile) {
      new CompileAllEvent().raise(this)
    }
    loseFocusIfAppropriate()
  }

  override protected def removeWidget(wrapper: WidgetWrapper): Unit = {
    remove(wrapper)

    // if the compile that is associated with this removal (assuming there is one) fails
    // the observer variables and constraints might not get reallocated in which case
    // if we try to add a different widget with the same name we get a constraint violation
    // from the old constraint. yuck.  ev 11/27/07
    new RemoveConstraintEvent(wrapper.widget.displayName).raise(this)

    Logger.logWidgetRemoved(wrapper.widget.classDisplayName,
        wrapper.widget.displayName)
  }

  /// loading and saving

  override def loadWidget(strings: Array[String], modelVersion: String): Widget =
    loadWidget(strings, modelVersion, 0, 0)

  // TODO: consider cleaning up this x and y business
  // it was added for copying/pasting widgets.
  // the regular loadWidget just uses the x and y from the string array
  // it passes in x=0, y=0 and we do a check. ugly, but works for now.
  // paste uses the x and y from the right click location.
  private def loadWidget(strings: Array[String], modelVersion: String, _x: Int, _y: Int): Widget = {
    val helper =
      new Widget.LoadHelper() {
        val version = modelVersion
        def convert(source: String, reporter: Boolean): String =
          workspace.autoConvert(source, true, reporter, modelVersion);
      }
    val widgetType = strings(0)
    val x = if (_x == 0) Integer.parseInt(strings(1)) else _x
    val parsedY = if (_y == 0) Integer.parseInt(strings(2)) else _y
    val y = if (viewWidget.isInstanceOf[ViewWidget] && !widgetType.equals("GRAPHICS-WINDOW") && VersionHistory.olderThan13pre1(modelVersion))
        parsedY + viewWidget.asInstanceOf[ViewWidget].getExtraHeight +
        viewWidget.asInstanceOf[ViewWidget].controlStrip.getHeight
      else
        parsedY
    if (widgetType.equals("GRAPHICS-WINDOW")) {
      // the graphics widget (and the command center) are special cases because
      // they are not recreated at load time, but reused
      viewWidget.asWidget.load(strings, helper)
      // in 3D we don't add the viewWidget to the interface panel
      // so don't worry about all the sizing junk ev 7/5/07
      val parent = viewWidget.asWidget.getParent
      if (parent != null) {
        parent.setSize(viewWidget.asWidget.getSize)
        enforceMinimumAndMaximumWidgetSizes(viewWidget.asWidget)
        parent.setLocation(x, y)
        zoomer.zoomWidgetLocation(
          getWrapper(viewWidget.asWidget),
                true, true, 1.0, zoomer.zoomFactor)
        zoomer.zoomWidgetSize(
          getWrapper(viewWidget.asWidget),
                true, true, 1.0, zoomer.zoomFactor)
        zoomer.scaleComponentFont(
          viewWidget.asInstanceOf[ViewWidget].view,
               zoomFactor, 1.0, false)
      }
      viewWidget.asWidget
    } else {
      val newGuy = makeWidget(widgetType, true)
      if (newGuy != null) {
        newGuy.load(strings, helper)
        enforceMinimumAndMaximumWidgetSizes(newGuy)
        addWidget(newGuy, x, y, false, true)
      }
      newGuy
    }
  }

  override def getWidgetsForSaving: JList[Widget] = {
    val result = new ArrayList[Widget]()
    val comps = getComponents
    // automatically add the view widget in 3D isn't not
    // in the comp list but we definitely want to save it
    // it won't be added twice as we're checking contains
    // below.  ev 7/5/07
    result.add(viewWidget.asInstanceOf[Widget])
    // loop backwards so JLayeredPane gives us the components
    // in back-to-front order for saving - ST 9/29/03
    var i = comps.length - 1
    while (i >= 0) {
      comps(i) match {
        case wrapper: WidgetWrapper =>
          val widget = wrapper.widget()
          if (!result.contains(widget))
            result.add(widget)
        case _ =>
      }
      i -= 1
    }
    result
  }

  override private[app] def contains(w: Editable): Boolean =
    if (w == viewWidget.asInstanceOf[Widget].getEditable)
      true
    else
      super.contains(w)

  override def handle(e: org.nlogo.window.Events.WidgetRemovedEvent): Unit = {
  }

  def handle(e: org.nlogo.window.Events.ExportInterfaceEvent): Unit = {
    try ImageIO.write(Images.paintToImage(this), "png", e.stream)
    catch {
      case ex: IOException =>
        e.exceptionBox(0) = ex
    }
  }

  @throws(classOf[java.io.IOException])
  private def exportInterface(): Unit = {
    try {
      val exportPath = SwingFileDialog.show(this,
        I18N.gui.get("dialog.interface.export.file"),
        AwtFileDialog.SAVE,
        workspace.guessExportName("interface.png"))
      var exception = Option.empty[IOException]
      val runExport = new Runnable() {
        def run(): Unit = {
          try workspace.exportInterface(exportPath)
          catch {
            case ex: IOException =>
              exception = Some(ex)
          }}}
      ModalProgressTask(Hierarchy.getFrame(this),
        I18N.gui.get("dialog.interface.export.task"), runExport)
      exception.foreach(e => throw e)
    } catch {
      case ex: UserCancelException => ApiExceptions.ignore(ex)
    }
  }

  def handle(e: LoadSectionEvent): Unit = {
    if (e.section == ModelSection.Interface)
      loadWidgets(e.lines, e.version)
  }

  override def removeAllWidgets(): Unit = {
    try {
      val comps = getComponents()
      setVisible(false)
      for (component <- getComponents) {
        component match {
          case w: WidgetWrapper if w.widget != viewWidget =>
            removeWidget(w)
          case _ =>
        }
      }
    } catch {
      case ex: RuntimeException => ApiExceptions.handle(ex)
    } finally {
      setVisible(false)
    }
  }

  /// buttons

  override def isFocusable: Boolean =
    getComponents.collect {
      case w: WidgetWrapper => w.widget
    }.exists {
      case _: InputBoxWidget => true
      case b: ButtonWidget   =>
        b.actionKey != '\u0000' && b.actionKey != ' '
      case _ => false
    }

  private def findActionButton(key: Char): ButtonWidget = {
    import java.lang.Character.toUpperCase
    getComponents.collect {
      case w: WidgetWrapper => w.widget
    }.collect {
      case b: ButtonWidget if toUpperCase(b.actionKey) == toUpperCase(key) => b
    }.headOption.orNull
  }

  private def enableButtonKeys(enabled: Boolean): Unit =
    getComponents.collect {
      case w: WidgetWrapper => w.widget
    }.foreach {
      case b: ButtonWidget => b.keyEnabled(enabled)
      case _ =>
    }

  def keyTyped(e: KeyEvent): Unit = {
    if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED &&
        !e.isActionKey &&
        (e.getModifiers & getToolkit.getMenuShortcutKeyMask) == 0) {
      val button = findActionButton(e.getKeyChar)
      if (button != null) {
        button.keyTriggered()
      }
    }
  }

  def keyPressed(evt: KeyEvent): Unit = { }

  def keyReleased(evt: KeyEvent): Unit = { }

  override def canAddWidget(widget: String): Boolean = {
    return (!widget.equals("Output")) || (getOutputWidget == null);
  }
}
