## MiniJava Compiler

This project is a combination of my last two assignments in the Compilers course. It consists of the front-end of a compiler for a small subset of Java called **MiniJava**. The MiniJava grammar  in BNF form can be found [here](http://cgi.di.uoa.gr/~thp06/project_files/minijava-new/minijava.html).</br>
The compiling process of each input file is consists of:
- The parsing of input, which is done automatically by certain tools (see Explanation).
- The static semantic analysis (type checking)
- The production of intermediate code (LLVM). [Clang](https://clang.llvm.org/) must be downloaded to run the produced code.
</br>

### Compile and Run
- make: compile all source files
- java Main inputFile1 inputFile2...: compile all the minijava files given
- clang-4.0 -o output_file input_file: run the produced llvm code.
- ./runAll.sh: A small **bash script** to automate the two steps above for all files in the directory "inputs-java".

### Explanation
[JavaCC](https://javacc.org/) LL(k) parser and JTB tool (JavaTreeBuilder) are used for parsing the given MiniJava files and making an actual **syntax tree** implementing on top of it a **Visitor pattern**.  From that point starts the actual project. The semantic analysis is performed utilizing the visitor pattern and traversing the syntax tree twice, one for filling symbol tables (with scopes of every variable, field, object etc.) and one for the actual type checking. For every input file that is semantically correct is produced the IR in [LLVM](https://llvm.org/docs/LangRef.html#instruction-reference), using a subset of its instructions. To run the .llvm files in out-llvm directory, use clang as instructioned above.
