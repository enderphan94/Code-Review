# Code-Review

# Automation tools

For Java:

* [Spotbug](https://github.com/spotbugs/spotbugs)

* [PMD](https://github.com/pmd/pmd)
1. Download the latest version of PMD
2. Decompress the file and compile the souce code as the [following](https://github.com/pmd/pmd/blob/master/BUILDING.md)
3. The files should be placed in `pmd-pmd_releases-6.25.0/pmd-dist/target/pmd-bin-6.25.0/bin`
4. `./run.sh pmd -d /Users/macbookpro/Downloads/java/contract.java -R ../../../../pmd-java/src/main/resources/category/java/bestpractices.xml -f html >> report_sample.html`

The rule files should be taken from `pmd-java/src/main/resources/category/java/`.

Read the [documentation](https://pmd.github.io/latest/pmd_userdocs_installation.html) for more details




# Manual Review
