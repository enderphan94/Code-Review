# Code-Review

Inspired by [OWAPS](https://owasp.org/www-community/Source_Code_Analysis_Tools)

# Automation tools

For Java:

* [Spotbug](https://github.com/spotbugs/spotbugs)

* [PMD](https://github.com/pmd/pmd) (.java)
1. Download the latest version of PMD
2. Decompress the file and compile the souce code as the [following](https://github.com/pmd/pmd/blob/master/BUILDING.md)
3. The files should be placed in `pmd-pmd_releases-6.25.0/pmd-dist/target/pmd-bin-6.25.0/bin`
4. `./run.sh pmd -d /Users/macbookpro/Downloads/java/contract.java -R ../../../../pmd-java/src/main/resources/category/java/bestpractices.xml -f html >> report_sample.html`

The rule files should be taken from `pmd-java/src/main/resources/category/java/`.

Read the [documentation](https://pmd.github.io/latest/pmd_userdocs_installation.html) for more details

* [Deep Dive](https://discotek.ca/deepdive.xhtml) (.apk, .jar)

1. Download the latest version of DeepDive
2. Decompress the zip file
3. The `run.sh` should be in `/bin`
4. It might return the error of `JAVA_HOME must point to a valid JRE (You may want to set it permanently in setenv.sh).`
5. Run these 2 commands to get rid of the above issue and pop up the GUI

`export CLASSPATH=../discotek.deepdive-1.5.5-beta.jar:../lib/discotek.deepdive-engine-1.5.5-beta.jar`

`java -Xmx4G -jar ../lib/discotek.deepdive-engine-1.5.5-beta.jar -decompile=true -project-directory=../sample-config -output-directory=/temp/report ../discotek.deepdive-1.5.5-beta.jar`

* [Semgrep](https://github.com/returntocorp/semgrep) (java, python, golang, javascript)

1. Clone the repo and read the Readme 
2. Run `semgrep --config=https://semgrep.live/c/p/java <PathToFile>`

* [VisualCodeGrepp](https://sourceforge.net/projects/visualcodegrepp/) (C++, C#, VB, PHP, Java, PL/SQL and COBOL)

1. Download the latest version 
2. Install it in WIndows
3. Select the language
4. Open the folder that contains the source code files.


# Manual Review
