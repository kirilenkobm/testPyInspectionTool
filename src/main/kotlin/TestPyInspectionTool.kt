package com.example.testpyinspectiontool

import com.intellij.codeInspection.*
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.*

class TestPyInspectionTool: LocalInspectionTool() {

    // list all subprocess functions that call subprocess and where
    // the inspection can be applicable
    private val subprocessFunctions = listOf("call", "run", "Popen")

    override fun getGroupDisplayName(): String {
        return "Unemployed"
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PyElementVisitor() {

            override fun visitPyCallExpression(node: PyCallExpression) {
                // node -> represents a function call in Python
                // callee -> part of the expression which is called
                // name -> it's name
                // return if null
                val callee = node.callee?.name ?: return

                // check whether we deal with subprocess.call, .Popen or .run
                // isCalleeText -> callee is a qualified reference with the given name
                if (callee in subprocessFunctions && node.isCalleeText("subprocess")) {
                    // get arguments passed to this call: type [PyExpression].
                    val args = node.arguments

                    if (args.isEmpty()) {
                        // do not do anything if empty
                        return
                    }

                    // first, capture whether the first argument is a string
                    // also, it can be a variable storing string
                    val firstArg = args[0]
                    // check whether it's a string
                    if (!isStringOrStringVariable(firstArg)) {
                        // not a string -> inspection is not applicable
                        return
                    }

                    // Check whether shell=True is used
                    for (arg in args) {
                        // find keyword arguments, skip the rest
                        if (arg !is PyKeywordArgument) {
                            continue
                        }
                        // extract keyword and it's value
                        val keyword = arg.keyword
                        val value = arg.valueExpression

                        if (keyword != "shell") {
                            continue
                        }
                        // this is shell=X, check whether it's true
                        if (value is PyBoolLiteralExpression && value.value) {
                            holder.registerProblem(
                                node,
                                "Unsafe subprocess call with shell=True",
                                ProblemHighlightType.WEAK_WARNING,
                                UnsafeSubprocessCallQuickFix(node)
                            )
                        }
                    }
                }
            }

            private fun isStringOrStringVariable(arg: PyExpression): Boolean {
                // function to check whether the first arg is a string,
                // or a variable that references a string
                if (arg is PyStringLiteralExpression) {
                    // it is a string -> nothing else to check here
                    return true
                }
                if (arg is PyReferenceExpression) {
                    // find the definition of the variable
                    val resolvedReference = arg.reference.resolve()
                    // do I handle case of unresolved reference?
                    // anyway, it is much bigger problem
                    if (resolvedReference is PyAssignmentStatement) {
                        val assignedValue = resolvedReference.assignedValue
                        if (assignedValue is PyStringLiteralExpression) {
                            // we got it
                            return true
                        }
                    }
                }
                // no: skip
                return false
            }
        }
    }
}


class UnsafeSubprocessCallQuickFix(call: PyCallExpression): LocalQuickFixOnPsiElement(call) {

    override fun getFamilyName() = "Convert to safe subprocess call"

    override fun getText() = "Convert to safe subprocess call"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val call = startElement as PyCallExpression

        // maybe just suggest.split()? Instead of this
        val command = getCommandAsString(call.arguments[0])
        val commandList = createCommandList(command)

        val newCall = PyElementGenerator.getInstance(project).createFromText(
            LanguageLevel.forElement(call),
            PyExpressionStatement::class.java,
            "subprocess.call($commandList)"
        )
        call.replace(newCall)
    }

    private fun getCommandAsString(arg: PyExpression): String {
        if (arg is PyStringLiteralExpression) {
            return arg.stringValue
        }

        if (arg is PyReferenceExpression) {
            val resolvedReference = arg.reference.resolve()
            if (resolvedReference is PyAssignmentStatement) {
                val assignedValue = resolvedReference.assignedValue
                if (assignedValue is PyStringLiteralExpression) {
                    return assignedValue.stringValue
                }
            }
        }
        return ""
    }

    private fun createCommandList(command: String): String {
        // split not only by spaces, but literally using whitespace char...
        // maybe a bad idea actually, but I'll fix it later
        // TODO: handle case where argument contains space or tab
        val commandParts = command.split(Regex("\\s+"))
        return commandParts.joinToString(", ", prefix = "[", postfix = "]") { "\"$it\"" }
    }
}
