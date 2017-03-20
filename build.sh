#!/bin/sh

cd "`dirname "$0"`"
mkdir -p dist lib

[ -f kotlinc/bin/kotlinc-jvm ]&& export PATH="`readlink -f ./kotlinc/bin`:$PATH"
target="$1"
shift

modules="`find modules -maxdepth 1 ! -samefile modules -printf '%f\n'`"
classpath="dist/node.kt.jar`find lib -name '*.jar' -printf ':%p'`"

case "$target" in
    all)            "$0" "node" "$@" && "$0" "modules" "$@" ;;
    clean)          rm -rf kotlinc lib dist ;;
    clean-modules)  echo "$modules" |while read mod;do
                        rm -f dist/node.kt-module-$mod.jar
                    done ;;
    clean-node)     rm -f dist/node.kt.jar ;;
    clean-libs)     rm -rf lib ;;
    clean-compiler) rm -rf kotlinc ;;
    modules)        echo "$modules" |while read mod;do
                        [ -f dist/node.kt-module-$mod.jar ]||kotlinc-jvm "$@" -d dist/node.kt-module-$mod.jar -cp "$classpath" `find modules/$mod/src -name '*.kt' -printf '%p '`
                    done ;;
    node)           kotlinc-jvm "$@" -d dist/node.kt.jar -cp "$classpath" `find src -name '*.kt' -printf '%p '` ;;
    libs)           cat depends.txt|while read lib;do
                        [ -f lib/`basename "$lib"` ]||(cd lib && wget "$@" "$lib")
                    done ;;
    compiler)       wget "$@" https://github.com/JetBrains/kotlin/releases/download/v1.1.1/kotlin-compiler-1.1.1.zip && unzip kotlin-compiler-1.1.1.zip && rm kotlin-compiler-1.1.1.zip ;;
    *)              cat <<EOF
Usage: $0 ACTION [ARGS...]
Where ACTION one of:
    all             Build node.kt and modules
    clean           Remove output jars and classes.
    clean-modules   Remove modules jars and classes.
    clean-node      Remove node.kt.jar and clsses.
    clean-compiler  Remove kotlin compiler.
    modules         Build only modules.
    node            Build node.kt without modules.
    libs            Download libraries.
    compiler        Download kotlin compiler.
    help            Show this help.
EOF
                    [ "$target" = help ]||exit 1 ;;
esac
