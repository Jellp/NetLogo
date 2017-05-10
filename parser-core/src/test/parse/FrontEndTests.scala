// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.parse

import org.scalatest.FunSuite
import org.nlogo.core
import org.nlogo.core.{ CompilerException, SourceLocation }
import org.nlogo.core.TestUtils.cleanJsNumbers

// This is where ExpressionParser gets most of its testing.  (It's a lot easier to test it as part
// of the overall front end than it would be to test in strict isolation.)

class FrontEndTests extends FunSuite {

  val PREAMBLE = "to __test [] "
  val POSTAMBLE = "\nend"

  /// helpers
  def compile(source: String, preamble: String = PREAMBLE, postamble: String = POSTAMBLE): Seq[core.Statements] =
    FrontEnd.frontEnd(preamble + source + postamble) match {
      case (procs, _) =>
        procs.map(_.statements)
    }

  /**
   * utility method useful for testing that start()
   * and end() return right answers everywhere
   */
  def statementsToString(ss: Seq[core.Statements], source: String) =
    (for (stmts <- ss) yield {
      val visitor = new PositionsCheckVisitor(source)
      visitor.visitStatements(stmts)
      visitor.buf.toString
    }).mkString
  /// helper
  def testStartAndEnd(source: String, preorderDump: String) {
    assertResult(preorderDump)(statementsToString(compile(source), source))
  }
  // preorder traversal
  class PositionsCheckVisitor(source: String) extends core.AstVisitor {
    val buf = new StringBuilder()
    override def visitCommandBlock(node: core.CommandBlock) { visit(node); super.visitCommandBlock(node) }
    override def visitReporterApp(node: core.ReporterApp) { visit(node); super.visitReporterApp(node) }
    override def visitReporterBlock(node: core.ReporterBlock) { visit(node); super.visitReporterBlock(node) }
    override def visitStatement(node: core.Statement) { visit(node); super.visitStatement(node) }
    override def visitStatements(node: core.Statements) {
      if (node.stmts.isEmpty)
        buf.append(node.getClass.getSimpleName + " '' ")
      else visit(node)
      super.visitStatements(node)
    }
    def visit(node: core.AstNode) {
      val start = node.start - PREAMBLE.length
      val end = node.end - PREAMBLE.length
      val text =
        try "'" + source.substring(start, end) + "'\n"
        catch { case _: StringIndexOutOfBoundsException =>
          "out of bounds: " + ((start, end)) }
      buf.append(node.getClass.getSimpleName + " " + text + " ")
    }
  }

  def runTest(input: String, result: String, preamble: String = PREAMBLE) {
    assertResult(cleanJsNumbers(result))(cleanJsNumbers(compile(input, preamble).mkString))
  }
  def runFailure(input: String, message: String, start: Int, end: Int, preamble: String = PREAMBLE) {
    doFailure(input, message, start, end, preamble)
  }
  def doFailure(input: String, message: String, start: Int, end: Int, preamble: String = PREAMBLE) {
    val e = intercept[CompilerException] { compile(input, preamble = preamble) }
    assertResult(message)(e.getMessage)
    val programText = preamble + input + POSTAMBLE
    def programTextWithContext(s: Int, e: Int) = {
      val beforeContext = programText.slice((s - 5) max 0, s)
      val t = programText.slice(s, e)
      val afterContext = programText.slice(e, (e + 5) min (programText.length - 1))
      s"... $beforeContext|$t|$afterContext ..."
    }
    val expectedErrorSource = programTextWithContext(start + preamble.length, end + preamble.length)
    val actualErrorSource = programTextWithContext(e.start, e.end)
    assert(start == e.start - preamble.length, s"incorrect start, expected: $expectedErrorSource but was actually $actualErrorSource\n")
    assert(end   == e.end   - preamble.length, s"incorrect end, expected: $expectedErrorSource but was actually $actualErrorSource")
  }

  /// now, the actual tests
  test("DoParseSimpleCommand") {
    runTest("__ignore round 0.5", "_ignore()[_round()[_const(0.5)[]]]")
  }
  test("DoParseCommandWithInfix") {
    runTest("__ignore 5 + 2", "_ignore()[_plus()[_const(5.0)[], _const(2.0)[]]]")
  }
  test("DoParseTwoCommands") {
    runTest("__ignore round 0.5 fd 5",
      "_ignore()[_round()[_const(0.5)[]]] " +
      "_fd()[_const(5.0)[]]")
  }
  test("parseSymbolUnknownName") {
    runTest("report __symbol foo", "_report()[_symbolstring()[_symbol()[]]]", preamble = "to-report sym ")
  }
  test("parseSymbolKnownName1") {
    runTest("report __symbol turtles", "_report()[_symbolstring()[_symbol()[]]]", preamble = "to-report sym ")
  }
  test("parseSymbolKnownName2") {
    runTest("report __symbol turtle", "_report()[_symbolstring()[_symbol()[]]]", preamble = "to-report sym ")
  }
  test("basic lambda parse") {
    runTest("__ignore [[x] -> x + x]",
      "_ignore()[_reporterlambda(X)[_plus()[_lambdavariable(X)[], _lambdavariable(X)[]]]]")
  }
  test("unbracketed lambda parse") {
    runTest("__ignore [x -> x + x]",
      "_ignore()[_reporterlambda(X)[_plus()[_lambdavariable(X)[], _lambdavariable(X)[]]]]")
  }
  test("unbracketed nested lambda parse") {
    runTest("__ignore [x -> [y ->  x / y ]]",
      "_ignore()[_reporterlambda(X)[_reporterlambda(Y)[_div()[_lambdavariable(X)[], _lambdavariable(Y)[]]]]]")
  }
  test("unbracketed zero-argument lambda parse") {
    runTest("__ignore [ -> 4]",
      "_ignore()[_reporterlambda()[_const(4)[]]]")
  }
  test("nested lambda parse") {
    runTest("__ignore [[x] -> [[y] ->  x / y ]]",
      "_ignore()[_reporterlambda(X)[_reporterlambda(Y)[_div()[_lambdavariable(X)[], _lambdavariable(Y)[]]]]]")
  }
  test("lambda parse with map") {
    runTest("__ignore map [[x] -> x] [1 2 3]",
      "_ignore()[_map()[_reporterlambda(X)[_lambdavariable(X)[]], _const([1, 2, 3])[]]]")
  }
  test("lambda parse with foreach") {
    runTest("foreach [1 2 3] [[x] -> __ignore x]",
      "_foreach()[_const([1, 2, 3])[], _commandlambda(X)[[_ignore()[_lambdavariable(X)[]]]]]")
  }
  test("DoParseVariadic") {
    runTest("__ignore list 1 2",
      "_ignore()[_list()[_const(1)[], _const(2)[]]]")
  }
  test("DoParseVariadic2") {
    runTest("__ignore (list 1 2 3)",
      "_ignore()[_list()[_const(1)[], _const(2)[], _const(3)[]]]")
  }
  test("DoParseMap") {
    runTest("__ignore map [[x] -> round x] [1.2 1.7 3.2]",
      "_ignore()[_map()[_reporterlambda(X)[_round()[_lambdavariable(X)[]]], _const([1.2, 1.7, 3.2])[]]]")
  }
  test("DoParseMapShortSyntax") {
    runTest("__ignore map round [1.2 1.7 3.2]",
      "_ignore()[_map()[_reporterlambda(_0)[_round()[_lambdavariable(_0)[]]], _const([1.2, 1.7, 3.2])[]]]")
  }
  test("DoParseForeachShortSyntax") {
    runTest("foreach [1 2 3] print",
      "_foreach()[_const([1.0, 2.0, 3.0])[], _commandlambda(_0)[[_print()[_lambdavariable(_0)[]]]]]")
  }
  test("DoParselet") {
    runTest("let x 5 __ignore x",
      "_let(Let(X))[_const(5.0)[]] _ignore()[_letvariable(Let(X))[]]")
  }
  test("DoParseParenthesizedCommand") {
    runTest("(__ignore 5)",
      "_ignore()[_const(5.0)[]]")
  }
  test("DoParseParenthesizedCommandAsFromEvaluator") {
    runTest("__observercode (__ignore 5) __done",
      "_observercode()[] " +
      "_ignore()[_const(5.0)[]] " +
      "_done()[]")
  }
  test("DoParseCarefully") {
    runTest("carefully [ error \"foo\" ] [ __ignore error-message ]",
      """_carefully()[[_error()[_const(foo)[]]], [_ignore()[_errormessage()[]]]]""")
  }
  test("ParseExpressionWithInfix1") {
    runTest("__ignore 5 + 2",
      "_ignore()[_plus()[_const(5.0)[], _const(2.0)[]]]")
  }
  test("ParseExpressionWithInfix2") {
    runTest("__ignore 5 + 2 * 7",
      "_ignore()[_plus()[_const(5.0)[], _mult()[_const(2.0)[], _const(7.0)[]]]]")
  }
  test("ParseExpressionWithInfix3") {
    runTest("__ignore 5 + 2 * 7 - 2",
      "_ignore()[_minus()[_plus()[_const(5.0)[], _mult()[_const(2.0)[], _const(7.0)[]]], _const(2.0)[]]]")
  }
  test("ParseExpressionWithInfixAndPrefix") {
    runTest("__ignore round 5.2 + log 64 2 * log 64 2 - random 2",
      "_ignore()[_minus()[_plus()[_round()[_const(5.2)[]], _mult()[_log()[_const(64.0)[], _const(2.0)[]], _log()[_const(64.0)[], _const(2.0)[]]]], _random()[_const(2.0)[]]]]")
  }
  test("ParseConstantInteger") {
    runTest("__ignore 5",
      "_ignore()[_const(5.0)[]]")
  }
  test("ParseConstantList") {
    runTest("__ignore [5]",
      "_ignore()[_const([5.0])[]]")
  }
  test("ParseConstantListWithSublists") {
    runTest("__ignore [[1] [2]]",
      "_ignore()[_const([[1.0], [2.0]])[]]")
  }
  test("ParseConstantListInsideLambda1") {
    runTest("__ignore n-values 10 [[]]",
      "_ignore()[_nvalues()[_const(10.0)[], _reporterlambda()[_const([])[]]]]")
  }
  test("ParseConstantListInsideLambda2") {
    runTest("__ignore n-values 10 [[5]]",
      "_ignore()[_nvalues()[_const(10.0)[], _reporterlambda()[_const([5.0])[]]]]")
  }
  test("ParseDiffuse") {
    runTest("diffuse pcolor 1",
      "_diffuse()[_patchvariable(2)[], _const(1.0)[]]")
  }
  test("ParseCodeBlock") {
    runTest("__ignore __block [ abc ]", "_ignore()[_block()[`[ abc ]`[]]]")
  }

  // in SetBreed2, we are checking that since no singular form of `fish`
  // is provided and it defaults to `turtle`, that the primitive `turtle`
  // isn't mistaken for a singular form and parsed as `_breedsingular` - ST 4/12/14
  test("SetBreed1") {
    runTest("__ignore turtle 0",
      "_ignore()[_turtle()[_const(0.0)[]]]")
  }
  test("SetBreed2") {
    runTest("__ignore turtle 0",
      "_ignore()[_turtle()[_const(0.0)[]]]")
  }
  test("DoParseBadCommand1") {
    runFailure("__ignore 1 2 3", "Expected command.", 11, 12)
  }
  test("DoParseBadCommand2") {
    runFailure("__ignore", "__IGNORE expected 1 input.", 0, 8)
  }
  test("DoParseReporterOnly") {
    runFailure("round 1.2", "Expected command.", 0, 5)
  }
  test("WrongArgumentType") {
    runFailure("__ignore count 0", "COUNT expected this input to be an agentset, but got a number instead", 15, 16)
  }
  test("WrongArgumentTypeAgentVariable") {
    runFailure("__ignore [not heading] of turtles", "NOT expected this input to be a TRUE/FALSE, but got a number instead", 14, 21)
  }
  test("tooManyCloseBrackets") {
    runFailure("ask turtles [ fd 1 ] ] ]", "Expected command.", 21, 22)
  }
  test("missingCloseBracket") {
    runFailure("crt 10 [ [", "No closing bracket for this open bracket.", 9, 10)
  }
  test("open paren errors with expected command") {
    runFailure("(", "Expected command.", 0, 1)
  }
  test("close paren errors with expected command") {
    runFailure(")", "Expected command.", 0, 1)
  }
  test("open bracket errors with expected command") {
    runFailure("[", "Expected command.", 0, 1)
  }
  test("close bracket errors with expected command") {
    runFailure("]", "Expected command.", 0, 1)
  }
  test("missing name after let") {
    runFailure("let", "LET expected 2 inputs, a variable name and any input.", 0, 3)
  }
  test("infix let misparse") {
    runFailure("let x * 5 5", "* expected a number on the left", 6, 7)
  }
  test("infix set misparse") {
    runFailure("let x 0 set x * 5 5", "* expected a number on the left", 14, 15)
  }
  test("infix show misparse") {
    runFailure("show * 5 5", "* expected a number on the left", 5, 6)
  }
  test("shows errors when verbatim code blocks don't match 1") {
    runFailure("__ignore __block [ abc", "No closing bracket for this open bracket.", 17, 18)
  }
  test("shows errors when verbatim code blocks don't match 2") {
    runFailure("__ignore __block [ (abc ]", "Expected close parenthesis here", 24, 25)
  }
  test("shows errors when verbatim code blocks don't match 3") {
    runFailure("__ignore __block [ ([abc) ]", "Expected close bracket here", 24, 25)
  }
  test("dangling argument errors as expected command") {
    runFailure("let _x 2 show 2 _x", "Expected command.", 16, 18)
  }
  test("run with no arguments") {
    runFailure("run", "RUN expected at least 1 input, a string or anonymous command and any input.", 0, 3)
  }
  test("foreach with no list") {
    runFailure("foreach [ x -> show x ]", "FOREACH expected at least 2 inputs, a list and an anonymous command.", 0, 7)
  }
  test("map with no arguments") {
    runFailure("show map", "MAP expected at least 2 inputs, an anonymous reporter and a list.", 5, 8)
  }
  test("map with bad first argument") {
    runFailure("show map 1 + 2", "MAP expected at least 2 inputs, an anonymous reporter and a list.", 5, 8)
  }
  test("unknown reporter failure") {
    runFailure("crt foo", "Nothing named FOO has been defined.", 4, 7)
  }
  // TODO: These are not great error messages - should mention unclosed parentheses
  test("list missing close paren") {
    runFailure("show (list 1 2", "No closing parenthesis for this open parenthesis.", 5, 6)
  }
  test("expects parenthesized expressions to contain only one statement") {
    runFailure("(show list 1 2 show 3)", "Expected a closing parenthesis here.", 15, 19)
  }
  test("foreach missing close paren") {
    runFailure("(foreach [1 2] [3 4] [ [x y] -> show x ]", "No closing parenthesis for this open parenthesis.", 0, 1)
  }
  test("errorsOnParseSymbolWithArgs") {
    runFailure("report __symbol turtle 0", "Expected command.", 23, 24, preamble = "to-report sym ")
  }
  // https://github.com/NetLogo/NetLogo/issues/348
  test("let of lambda variable") {
    runFailure("foreach [1] [[x] -> let x 0 ]", "There is already a local variable here called X", 24, 25)
  }
  test("error-message used outside of carefully") {
    runFailure("let foo error-message", "error-message cannot be used outside of CAREFULLY.", 8, 21)
  }
  test("lambda argument name is a literal") {
    runFailure("__ignore [[2] -> 2]", "Expected a variable name here", 11, 12)
  }
  test("invalidLiteral") {
    runFailure("__ignore [", "No closing bracket for this open bracket.", 9, 10)
  }
  test("invalidLambda1") {
    runFailure("__ignore [->", "No closing bracket for this open bracket.", 9, 10)
  }
  test("invalidLambda2") {
    runFailure("__ignore [[->", "No closing bracket for this open bracket.", 10, 11)
  }
  test("invalidLambda3") {
    runFailure("__ignore [[]->", "No closing bracket for this open bracket.", 9, 10)
  }
  test("invalidLambda4") {
    runFailure("__ignore [ foo bar -> ]", "An anonymous procedures of two or more arguments must enclose its argument list in brackets", 11, 18)
  }
  test("lambda argument shadows primitive name") {
    runFailure("__ignore [[turtles] -> 2]", "There is already a primitive reporter called TURTLES", 11, 18)
  }
  test("unbracketed lambda argument shadows primitive name") {
    runFailure("__ignore [turtles -> 2]", "There is already a primitive reporter called TURTLES", 10, 17)
  }
  test("lambda argument duplicate name") {
    runFailure("__ignore [[bar bar] -> 2]", "There is already a local variable here called BAR", 15, 18)
  }
  test("lambda argument duplicate nested name") {
    runFailure("__ignore [[bar] -> [[bar] -> 2]]", "There is already a local variable here called BAR", 21, 24)
  }
  test("unbracketed lambda argument duplicate nested name") {
    runFailure("__ignore [bar -> [bar -> 2]]", "There is already a local variable here called BAR", 18, 21)
  }
  test("lambda argument shadows local variable") {
    runFailure("let baz 7 __ignore [[baz] -> 3]", "There is already a local variable here called BAZ", 21, 24)
  }
  test("unbracketed lambda argument shadows local variable") {
    runFailure("let baz 7 __ignore [baz -> 3]", "There is already a local variable here called BAZ", 20, 23)
  }
  test("lambda argument shadows procedure name") {
    runFailure("let baz [[baz] -> 3]", "There is already a local variable here called BAZ", 10, 13)
  }
  test("unbracketed lambda argument shadows procedure name") {
    runFailure("let baz [baz -> 3]", "There is already a local variable here called BAZ", 9, 12)
  }
  test("lambda argument shadows procedure variable") {
    runFailure("__ignore [[bar] -> 2]", "There is already a local variable here called BAR", 11, 14, "to foo [bar] ")
  }
  test("DoParseForeach") {
    runFailure("foreach [1 2 3] [__ignore ?]", "Nothing named ? has been defined.", 26, 27)
  }
  test("DoParseForeachWithDone") {
    runFailure("foreach [1 2 3] __done", "FOREACH expected at least 2 inputs, a list and an anonymous command.", 0, 7)
  }

  /// tests using testStartAndEnd
  test("StartAndEndPositions0") {
    testStartAndEnd("ca", Seq(
      "Statements 'ca'",
      "Statement 'ca'").mkString("", "\n ", "\n "))
  }
  test("StartAndEndPositions1") {
    testStartAndEnd("__ignore 5", Seq(
      "Statements '__ignore 5'",
      "Statement '__ignore 5'",
      "ReporterApp '5'").mkString("", "\n ", "\n "))
  }
  test("StartAndEndPositions2") {
    testStartAndEnd("__ignore n-values 5 [[] -> world-width]", Seq(
      "Statements '__ignore n-values 5 [[] -> world-width]'",
      "Statement '__ignore n-values 5 [[] -> world-width]'",
      "ReporterApp 'n-values 5 [[] -> world-width]'",
      "ReporterApp '5'",
      "ReporterApp '[[] -> world-width]'",
      "ReporterApp 'world-width'").mkString("", "\n ", "\n "))
  }
  test("StartAndEndPositions8") {
    testStartAndEnd("crt 1", Seq(
      "Statements 'crt 1'",
      "Statement 'crt 1'",
      "ReporterApp '1'",
      "CommandBlock ''",
      "Statements ''").mkString("", "\n ", " "))
  }
  test("StartAndEndPositions9") {
    testStartAndEnd("crt 1 [ ]", Seq(
      "Statements 'crt 1 [ ]'",
      "Statement 'crt 1 [ ]'",
      "ReporterApp '1'",
      "CommandBlock '[ ]'",
      "Statements ''").mkString("", "\n ", " "))
  }

  test("StartAndEndPositions10") {
    testStartAndEnd("ask turtles with [color = red ] [ fd 1 ]", Seq(
      "Statements 'ask turtles with [color = red ] [ fd 1 ]'",
      "Statement 'ask turtles with [color = red ] [ fd 1 ]'",
      "ReporterApp 'turtles with [color = red ]'",
      "ReporterApp 'turtles'",
      "ReporterBlock '[color = red ]'",
      "ReporterApp 'color = red'",
      "ReporterApp 'color'",
      "ReporterApp 'red'",
      "CommandBlock '[ fd 1 ]'",
      "Statements 'fd 1'",
      "Statement 'fd 1'",
      "ReporterApp '1'").mkString("", "\n ", "\n "))
  }

  test("While") {
    testStartAndEnd("while [count turtles < 10] [ crt 1 ]", Seq(
      "Statements 'while [count turtles < 10] [ crt 1 ]'",
      "Statement 'while [count turtles < 10] [ crt 1 ]'",
      "ReporterBlock '[count turtles < 10]'",
      "ReporterApp 'count turtles < 10'",
      "ReporterApp 'count turtles'",
      "ReporterApp 'turtles'",
      "ReporterApp '10'",
      "CommandBlock '[ crt 1 ]'",
      "Statements 'crt 1'",
      "Statement 'crt 1'",
      "ReporterApp '1'",
      "CommandBlock ''",
      "Statements ''").mkString("", "\n ", " "))
  }

  // issue #417 (source positions for literal lists)
  test("literal list") {
    testStartAndEnd("print [1 2 3]", Seq(
      "Statements 'print [1 2 3]'",
      "Statement 'print [1 2 3]'",
      "ReporterApp '[1 2 3]'").mkString("", "\n ", "\n "))
  }

  /// duplicate name tests

  def duplicateName(s: String, err: String) = {
    val e = intercept[CompilerException] {
      FrontEnd.frontEnd(s, extensionManager = extensionManager)
    }
    assertResult(err)(e.getMessage)
  }

  test("LetSameNameAsCommandProcedure2") {
    duplicateName("to b let a 5 end  to a end",
      "There is already a procedure called A")
  }
  test("LetSameNameAsReporterProcedure2") {
    duplicateName("to b let a 5 end  to-report a end",
      "There is already a procedure called A")
  }
  test("LetNameSameAsEnclosingCommandProcedureName") {
    duplicateName("to bazort let bazort 5 end",
      "There is already a procedure called BAZORT")
  }
  test("LetNameSameAsEnclosingReporterProcedureName") {
    duplicateName("to-report bazort let bazort 5 report bazort end",
      "There is already a procedure called BAZORT")
  }
  test("LetNameSameAsBreedVariableName") {
    duplicateName("breed [mice mouse] mice-own [fur] to foo let fur 5 end",
      "There is already a MICE-OWN variable called FUR")
  }
  test("BreedDuplicateName") {
    duplicateName("breed [xs xs]",
      "There is already a breed called XS")
  }
  test("BreedOnlyOneName") {
    duplicateName("breed [xs]",
      "Breed declarations must have plural and singular. BREED [XS] has only one name.")
  }
  test("LinkBreedOnlyOneName") {
    duplicateName("directed-link-breed [xs]",
      "Breed declarations must have plural and singular. DIRECTED-LINK-BREED [XS] has only one name.")
  }
  test("BreedPrimSameNameAsBuiltInPrim") {
    duplicateName("breed [strings string]",
      "Defining a breed [STRINGS STRING] redefines IS-STRING?, a primitive reporter")
  }
  test("BreedPrimSameAsProcedure") {
    duplicateName("breed [mice mouse] to-report mice-at report nobody end",
      "There is already a breed reporter called MICE-AT")
  }
  test("SameLocalVariableTwice1") {
    duplicateName("to a1 locals [b b] end",
      "Nothing named LOCALS has been defined.")
  }
  test("SameLocalVariableTwice2") {
    duplicateName("to a2 [b b] end",
      "There is already a local variable called B here")
  }
  test("SameLocalVariableTwice3") {
    duplicateName("to a3 let b 5 let b 6 end",
      "There is already a local variable here called B")
  }
  test("SameLocalVariableTwice4") {
    duplicateName("to a4 locals [b] let b 5 end",
      "Nothing named LOCALS has been defined.")
  }
  test("SameLocalVariableTwice5") {
    duplicateName("to a5 [b] locals [b] end",
      "Nothing named LOCALS has been defined.")
  }
  test("SameLocalVariableTwice6") {
    duplicateName("to a6 [b] let b 5 end",
      "There is already a local variable here called B")
  }

  test("SameNameAsExtensionPrim") {
    duplicateName("to foo:bar end",
      "There is already an extension command called FOO:BAR")
  }

  test("findIncludes lists all includes when there is a valid includes statement") {
    assertResult(Seq())(FrontEnd.findIncludes(""))
    assertResult(Seq("foo.nls"))(FrontEnd.findIncludes("__includes [\"foo.nls\"]"))
    assertResult(Seq("foo.nls"))(FrontEnd.findIncludes("__includes [\"foo.nls\"] to foo show \"bar\" end"))
    assertResult(Seq("foo.nls"))(FrontEnd.findIncludes("__includes [\"foo.nls\"] foo \"bar\" end"))
    assertResult(Seq("foo.nls", "bar"))(FrontEnd.findIncludes("__includes [\"foo.nls\" foo \"bar\" end"))
  }

  test("findProcedurePositions maps procedures to their critical syntax tokens") {
    import org.nlogo.core.{ Token, TokenType }
    val procedurePos = FrontEnd.findProcedurePositions("""to foo show "bar" end""", None).get("foo")
    assert(procedurePos.nonEmpty)
    assert(procedurePos.get.declarationKeyword == Token("to", TokenType.Keyword, "TO")(SourceLocation(0, 2, "")))
    assert(procedurePos.get.identifier         == Token("foo", TokenType.Ident, "FOO")(SourceLocation(3, 6, "")))
    assert(procedurePos.get.endKeyword         == Token("end", TokenType.Keyword, "END")(SourceLocation(18, 21, "")))

    val procedurePos2 = FrontEnd.findProcedurePositions("""to foo end to bar show "foo" end""", None).get("bar")
    assert(procedurePos2.nonEmpty)
  }

  test("findProcedurePositions maps procedures to critical syntax tokens in a way that is tolerant of errors") {
    import org.nlogo.core.{ Token, TokenType }
    val procedurePos = FrontEnd.findProcedurePositions("""to foo show "bar" to bar show "foo" end""", None).get("foo")
    assert(procedurePos.get.identifier == Token("foo", TokenType.Ident, "FOO")(SourceLocation(3, 6, "")))
    assert(procedurePos.get.endKeyword == Token("end", TokenType.Keyword, "END")(SourceLocation(36, 39, "")))
    val unclosedProcedure = FrontEnd.findProcedurePositions("""to foo show""", None).get("foo")
    assert(unclosedProcedure.nonEmpty)
    assert(unclosedProcedure.get.identifier.text == "foo")
    val barProcedure = FrontEnd.findProcedurePositions("""to bar show "foo" end""", None).get("bar")
    assert(barProcedure.nonEmpty)
    assert(barProcedure.get.identifier.text == "bar")
    val noNameProcedure = FrontEnd.findProcedurePositions("""to show "foo" end""", None)
    assert(noNameProcedure.isEmpty)
  }

  lazy val extensionManager = new core.DummyExtensionManager() {
      override def anyExtensionsLoaded = true
      override def replaceIdentifier(name: String): core.Primitive =
        name match {
          case "FOO:BAR" => new core.PrimitiveCommand() {
            override def getSyntax: core.Syntax =
              core.Syntax.commandSyntax(right = List(core.Syntax.ListType))
          }
          case "FOO:BAZ" => new core.PrimitiveReporter() {
            override def getSyntax: core.Syntax =
              core.Syntax.reporterSyntax(right = List(), ret = core.Syntax.ListType)
          }
          case _ => null
        }
    }

  def colorTokenize(source: String): Seq[core.Token] = {
    FrontEnd.tokenizeForColorization(source, core.NetLogoCore, extensionManager)
  }

  def assertColorTokenize(source: String, expectedTypes: Seq[core.TokenType]): Unit = {
    val toks = colorTokenize(source)
    (toks zip expectedTypes).foreach {
      case (t, expectedType) => assertResult(expectedType)(t.tpe)
    }
  }

  test("tokenizeForColorization tokenizes unknown values as Ident") {
    assertColorTokenize("foobarbaz", Seq(core.TokenType.Ident))
  }
  test("tokenizeForColorization tags commands with the correct token type") {
    assertColorTokenize("fd 1", Seq(core.TokenType.Command, core.TokenType.Literal))
  }
  test("tokenizeForColorization tags reporters with correct token type") {
    assertColorTokenize("list 1 2 3", Seq(core.TokenType.Reporter))
  }
  test("tokenizeForColorization tags keywords with correct token type") {
    assertColorTokenize("to foo", Seq(core.TokenType.Keyword, core.TokenType.Ident))
  }
  test("tokenizeForColorization tags extension prims with correct token type") {
    assertColorTokenize("foo:bar foo:baz",
      Seq(core.TokenType.Command, core.TokenType.Reporter))
  }
  test("tokenizeForColorization tags agent variables with correct token type") {
    assertColorTokenize("set pcolor color",
      Seq(core.TokenType.Command, core.TokenType.Reporter, core.TokenType.Reporter))
  }
}
