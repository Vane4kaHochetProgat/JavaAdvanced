#!/bin/bash

implPath="../java-solutions/info/kgeorgiy/ja/murashov/implementor/Implementor.java"

javac -cp "../../java-advanced-2022/artifacts/info.kgeorgiy.java.advanced.implementor.jar" "${implPath}"
cd "../java-solutions"
jar cmvf "../META-INF/MANIFEST.MF" "../scripts/Implementor.jar" "info/kgeorgiy/ja/murashov/implementor/Implementor.class" "info/kgeorgiy/ja/murashov/implementor/Implementor\$1.class" "info/kgeorgiy/ja/murashov/implementor/Implementor\$2.class" "info/kgeorgiy/ja/murashov/implementor/Implementor\$MethodWrapper.class"