#!/bin/bash

junit="../../java-advanced-2022/lib/quickcheck-0.6.jar:../../java-advanced-2022/lib/junit-4.11.jar"
implPath="../java-solutions/info/kgeorgiy/ja/murashov/implementor/Implementor.java"
mainModule="../../java-advanced-2022/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/"
mainModuleImplerException="${mainModule}ImplerException.java"
mainModuleJarImpler="${mainModule}JarImpler.java"
mainModuleImpler="${mainModule}Impler.java"
javadoc -d "../Javadoc" -cp ${junit} ${implPath} ${mainModuleImplerException} ${mainModuleJarImpler} ${mainModuleImpler}