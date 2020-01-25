@echo off
set stage=PA3
if "%2"=="" (
    if "%1"=="compile" (
        call gradlew build
        echo gradlew build done
        goto :end
    ) else if "%1"=="test" (
        call gradlew build
        echo gradlew build done
        java -jar -ea --enable-preview build/libs/decaf.jar -t %stage% --log-color TestCases\test.decaf --log-level all
        rm test.tac
        goto :end
    ) else if "%1"=="tac" (
        call gradlew build
        echo gradlew build done
        java -jar -ea --enable-preview build/libs/decaf.jar -t %stage% --log-color TestCases\test.decaf --log-level all
        goto :end
    ) else if "%1"=="output" (
        java -jar -ea --enable-preview build/libs/decaf.jar -t %stage% --log-color TestCases\test.decaf > TestCases\test.output
        rm test.tac
        goto :end
    ) else if "%1"=="clean" (
        rm -r build
        goto :end
    ) else (
        :show_help
        echo Usage:
        echo Compile: test compile
        echo Compile and run: test {stage} {testcase}
        echo Compile and run custom test: test test
        echo Output custom test result to test.output: test output
        echo Compile and run custom test and keep TAC: test tac
        echo Run: test {stage} {testcase} run
        echo Generate testcase: test {stage} {testcase} gen
        echo Compile and run but do not diff: test {stage} {testcase} nodiff
        echo Compile and run and output TAC but do not diff: test {stage} {testcase} tac
        echo Delete build/ , compile and run: test {stage} {testcase} full
        echo Delete build/: test clean
        echo {stage}: [S1, S1-LL, S2, S3]
        goto :end
    )
)

if "%3" == "" (
    call gradlew build
    echo gradlew build done
) else if "%3" == "run" (
    rem a
) else if "%3" == "gen" (
    rem a
) else if "%3" == "full" (
    rm -r build
    call gradlew build
    echo gradlew build done
) else if "%3" == "nodiff" (
    call gradlew build
    echo gradlew build done
) else if "%3" == "tac" (
    call gradlew build
    echo gradlew build done
) else (
    goto :show_help
)
java -jar -ea --enable-preview build/libs/decaf.jar -t %stage% --log-color TestCases\%1\%2.decaf --log-level all
if "%3" neq "nodiff" if "%3" neq "tac" (
    java -jar -ea --enable-preview build/libs/decaf.jar -t %stage% --log-color TestCases\%1\%2.decaf > TestCases\%1\output\%2.output
)
if "%3" == "gen" (
    copy TestCases\%1\output\%2.output TestCases\%1\result\%2.result
) else if "%3" neq "nodiff" if "%3" neq "tac" (
    fc TestCases\%1\output\%2.output TestCases\%1\result\%2.result
)
if "%3" neq "tac" (
    rm %2.tac
)

:end