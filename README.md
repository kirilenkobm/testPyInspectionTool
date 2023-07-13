# testPyInspectionTool WIP

Far from perfect.
At least it starts PyCharm, but the plugin does not work.
Seems like some java issues.

I am just learning how to make inspections for Python code compatible with PyCharm.
Not sure whether this inspection is useful, but the idea is to catch the situation where subprocess is used in the following way:

```python
subprocess.call("program arg1 arg2", shell=True)
```

And suggests a quick fix like:

```python
subprocess.call(["program", "arg1", "arg2"])
```


If `shell=True` is provided as an argument, it instructs Python to run the command through the shell. This means that shell syntax and features like shell variables, globs, and other special characters will be processed before the command is run.
While this can be convenient, it can also lead to potential security vulnerabilities, especially when combined with string formatting operations to build the command.

Logic is still to be corrected.
