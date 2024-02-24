#!/bin/bash
javac *.java

if [ $? -eq 0 ]; then
    echo "Successfully Compiled All Files."


else echo "Failed To Compile One or More Files."

fi