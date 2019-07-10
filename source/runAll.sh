#!/bin/bash

#mikro sciptaki gia na trexei ola ta test cases ston fakelo 'inputs'!

total_count=0
corr_count=0
indir=../inputs-java/*

#check if inputs directory exists
if [ ! -e "../inputs-java" ]
	then echo "Inputs directory given doesn't exist. Exiting..."
	exit
fi

#create directory for executable files (from clang) if it doesn't exist
if [ ! -e "./executables" ]
	then mkdir executables
fi

for file in $indir
do
	let "total_count=$total_count+1"
	java Main $file
	excode=`echo $?`
	if [ "$excode" -eq "0" ]
	then
		#extract file name and run .ll file
		filename=${file##*/}				#get everything after last slash
		filename=${filename%.*}				#omit ".java"
		llfile="./out-llvm/$filename.ll"
		clang-4.0 -o ./executables/$filename $llfile

		excode=`echo $?`
		if [ "$excode" -eq "0" ]
		then
        	let "corr_count=$corr_count+1"
        	echo "$file: No problems encountered!"
		else
			echo "$file: An error was encountered..."
		fi
    else
        echo "$file: An error was encountered..."
	fi
done

echo -e "\n\n TOTAL RESULT: $corr_count / $total_count files.\n -> .ll files in 'out-llvm' directory, out files in 'executables' directory."
