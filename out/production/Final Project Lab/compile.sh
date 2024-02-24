#!/bin/bash
javac *.java

if [ $? -eq 0 ]; then
    echo "Successfully Compiled All Files."

#javac Webserver.java

else echo "Failed To Compile One or More Files."

fi