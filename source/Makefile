all: compile

compile:
	java -jar jtb132di.jar -te minijava.jj
	java -jar javacc5.jar minijava-jtb.jj
	javac *.java

clean:
	rm -f *.class *~

count:
	wc -l Main.java OffsetBlock.java STableImpl.java ClassSt.java MethodSt.java Signature.java STableVisitor.java LLVMVisitor.java ListStack.java vtable_cl.java VTablesImpl.java CustomPair.java Counters.java runAll.sh
