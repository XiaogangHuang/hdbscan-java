#!/bin/sh

DIR="data#6"

# rm -rf *.results

s=1.0

dataset() {
    for n in 16 32 64 128 256 512 1024;
    do
		for i in $(seq 1)
		do
            # IHDBSCAN
            # args: dataset, minpoints, run, outputfiles, filter
            java -jar -Xmx61g -XX:+UseConcMarkSweepGC -XX:+UseParNewGC IHDBSCAN.jar "${DIR}/16d-${n}.dat" 16 $i false true ${s} >> RNG.ihdbscan-dataset-filter.results

            # HDBSCAN
            # java -jar -Xmx60g HDBSCAN.jar "${DIR}/16d-${n}.dat" 16 $i false >> hdbscan-dataset.results
        done
    done
}

minpoints() {
    for minpoints in 2 4 8 16 32 64 128;
    do
		for i in $(seq 1)
		do
            # IHDBSCAN
            # args: dataset, minpoints, run, outputfiles, filter
	        java -jar -Xmx61g -XX:+UseConcMarkSweepGC -XX:+UseParNewGC IHDBSCAN.jar "${DIR}/16d-128.dat" ${minpoints} $i false true ${s} >> RNG.ihdbscan-minpoints-filter.results

            # HDBSCAN
            # java -jar -Xmx60g HDBSCAN.jar "${DIR}/16d-128.dat" ${minpoints} $i false >> hdbscan-minpoints.results
        done
    done
}

dimensions() {
    for d in 2 4 8 16 32 64 128;
    do
		for i in $(seq 1)
		do
            # IHDBSCAN
            # args: dataset, minpoints, run, outputfiles, filter
	        java -jar -Xmx61g -XX:+UseConcMarkSweepGC -XX:+UseParNewGC IHDBSCAN.jar "${DIR}/${d}d-128.dat" 16 $i false true ${s} >> RNG.ihdbscan-dimensions-filter.results

            # HDBSCAN
            # java -jar -Xmx60g HDBSCAN.jar "${DIR}/${d}d-128.dat" 16 $i false >> hdbscan-dimensions.results
        done
    done
}

minpoints
dimensions
dataset
