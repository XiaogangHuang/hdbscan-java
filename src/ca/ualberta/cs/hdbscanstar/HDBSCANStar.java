package ca.ualberta.cs.hdbscanstar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import ca.ualberta.cs.SHM.HMatrix.HMatrix;
import ca.ualberta.cs.SHM.HMatrix.ObjInstance;
import ca.ualberta.cs.distance.DistanceCalculator;
import ca.ualberta.cs.hdbscanstar.Constraint.CONSTRAINT_TYPE;
import ca.ualberta.cs.hdbscanstar.HDBSCANStarRunner.WrapInt;
import ca.ualberta.cs.util.Dataset;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashBigSet;

/**
 * Implementation of the HDBSCAN* algorithm, which is broken into several methods.
 * @author zjullion
 */
public class HDBSCANStar implements Serializable {

	// ------------------------------ PRIVATE VARIABLES ------------------------------

	// ------------------------------ CONSTANTS ------------------------------

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	public static final String WARNING_MESSAGE = 
			"----------------------------------------------- WARNING -----------------------------------------------\n" + 
					"With your current settings, the K-NN density estimate is discontinuous as it is not well-defined\n" +
					"(infinite) for some data objects, either due to replicates in the data (not a set) or due to numerical\n" + 
					"roundings. This does not affect the construction of the density-based clustering hierarchy, but\n" +
					"it affects the computation of cluster stability by means of relative excess of mass. For this reason,\n" +
					"the post-processing routine to extract a flat partition containing the most stable clusters may\n" +
					"produce unexpected results. It may be advisable to increase the value of MinPts and/or M_clSize.\n" +
					"-------------------------------------------------------------------------------------------------------";


	private static final int FILE_BUFFER_SIZE = 32678;

	// ------------------------------ CONSTRUCTORS ------------------------------

	// ------------------------------ PUBLIC METHODS ------------------------------

	/**
	 * Reads in the input data set from the file given, assuming the delimiter separates attributes
	 * for each data point, and each point is given on a separate line.  Error messages are printed
	 * if any part of the input is improperly formatted.
	 * @param fileName The path to the input file
	 * @param delimiter A regular expression that separates the attributes of each point
	 * @return A double[][] where index [i][j] indicates the jth attribute of data point i
	 * @throws IOException If any errors occur opening or reading from the file
	 */
	public static double[][] readInDataSet(String fileName, String delimiter) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(fileName));
		ArrayList<double[]> dataSet = new ArrayList<double[]>();
		int numAttributes = -1;
		int lineIndex = 0;

		String line = reader.readLine();

		while (line != null) {
			lineIndex++;
			String[] lineContents = line.split(delimiter);

			if (numAttributes == -1)
				numAttributes = lineContents.length;
			else if (lineContents.length != numAttributes)
				System.err.println("Line " + lineIndex + " of data set has incorrect number of attributes.");

			double[] attributes = new double[numAttributes];
			for (int i = 0; i < numAttributes; i++) {
				try {
					//If an exception occurs, the attribute will remain 0:
					attributes[i] = Double.parseDouble(lineContents[i]);
				}
				catch (NumberFormatException nfe) {
					System.err.println("Illegal value on line " + lineIndex + " of data set: " + lineContents[i]);
				}
			}

			dataSet.add(attributes);
			line = reader.readLine();
		}
		
		reader.close();
		double[][] finalDataSet = new double[dataSet.size()][numAttributes];

		for (int i = 0; i < dataSet.size(); i++) {
			finalDataSet[i] = dataSet.get(i);
		}

		return finalDataSet;
	}


	/**
	 * Reads in constraints from the file given, assuming the delimiter separates the points involved
	 * in the constraint and the type of the constraint, and each constraint is given on a separate 
	 * line.  Error messages are printed if any part of the input is improperly formatted.
	 * @param fileName The path to the input file
	 * @param delimiter A regular expression that separates the points and type of each constraint
	 * @return An ArrayList of Constraints
	 * @throws IOException If any errors occur opening or reading from the file
	 */
	public static ArrayList<Constraint> readInConstraints(String fileName, String delimiter) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(fileName));
		ArrayList<Constraint> constraints = new ArrayList<Constraint>();
		int lineIndex = 0;
		String line = reader.readLine();

		while (line != null) {
			lineIndex++;
			String[] lineContents = line.split(delimiter);

			if (lineContents.length != 3)
				System.err.println("Line " + lineIndex + " of constraints has incorrect number of elements.");

			try {
				int pointA = Integer.parseInt(lineContents[0]);
				int pointB = Integer.parseInt(lineContents[1]);
				CONSTRAINT_TYPE type = null;

				if (lineContents[2].equals(Constraint.MUST_LINK_TAG))
					type = CONSTRAINT_TYPE.MUST_LINK;
				else if (lineContents[2].equals(Constraint.CANNOT_LINK_TAG))
					type = CONSTRAINT_TYPE.CANNOT_LINK;
				else
					throw new NumberFormatException();

				constraints.add(new Constraint(pointA, pointB, type));
			}
			catch (NumberFormatException nfe) {
				System.err.println("Illegal value on line " + lineIndex + " of data set: " + line);
			}

			line = reader.readLine();
		}

		reader.close();
		return constraints;
	}


	/**
	 * Calculates the core distances for each point in the data set, given some value for k.
	 * @param dataSet A double[][] where index [i][j] indicates the jth attribute of data point i
	 * @param k Each point's core distance will be it's distance to the kth nearest neighbor
	 * @param distanceFunction A DistanceCalculator to compute distances between points
	 * @return An array of core distances
	 */
	public static double[] calculateCoreDistances(Dataset dataSet, int k, DistanceCalculator distanceFunction) {
		int numNeighbors = k -1;
		double[] coreDistances = new double[dataSet.length()];

		if (k == 1) {

			for (int point = 0; point < dataSet.length(); point++) {
				coreDistances[point] = 0;
			}

			return coreDistances;
		}

		for (int point = 0; point < dataSet.length(); point++) {
			double[] kNNDistances = new double[numNeighbors];	//Sorted nearest distances found so far
			for (int i = 0; i < numNeighbors; i++) {
				kNNDistances[i] = Double.MAX_VALUE;
			}

			for (int neighbor = 0; neighbor < dataSet.length(); neighbor++) {
				if (point == neighbor)
					continue;
				double distance = dataSet.computeDistance(point, neighbor);

				//Check at which position in the nearest distances the current distance would fit:
				int neighborIndex = numNeighbors;
				while (neighborIndex >= 1 && distance < kNNDistances[neighborIndex-1]) {
					neighborIndex--;
				}

				//Shift elements in the array to make room for the current distance:
				if (neighborIndex < numNeighbors) {
					for (int shiftIndex = numNeighbors - 1; shiftIndex > neighborIndex; shiftIndex--) {
						kNNDistances[shiftIndex] = kNNDistances[shiftIndex-1];
					}
					kNNDistances[neighborIndex] = distance;
				}
			}
			coreDistances[point] = kNNDistances[numNeighbors-1];
		}

		return coreDistances;
	}


	/**
	 * Constructs the minimum spanning tree of mutual reachability distances for the data set, given
	 * the core distances for each point.
	 * @param dataSet A double[][] where index [i][j] indicates the jth attribute of data point i
	 * @param coreDistances An array of core distances for each data point
	 * @param selfEdges If each point should have an edge to itself with weight equal to core distance
	 * @param distanceFunction A DistanceCalculator to compute distances between points
	 * @return An MST for the data set using the mutual reachability distances
	 */
	public static UndirectedGraph constructMST(Dataset dataSet, double[][] coreDistances, int minPoints,
			boolean selfEdges, DistanceCalculator distanceFunction) {

		minPoints--;
		int selfEdgeCapacity = 0;
		if (selfEdges)
			selfEdgeCapacity = dataSet.length();

		// One bit is set (true) for each attached point, or unset (false) for unattached points:
		BitSet attachedPoints = new BitSet(dataSet.length());

		// Each point has a current neighbor point in the tree, and a current nearest distance:
		int[] nearestMRDNeighbors = new int[dataSet.length()-1 + selfEdgeCapacity];
		double[] nearestMRDDistances = new double[dataSet.length()-1 + selfEdgeCapacity];

		for (int i = 0; i < dataSet.length()-1; i++) {
			nearestMRDDistances[i] = Double.MAX_VALUE;
		}

		// The MST is expanded starting with the last point in the data set:
		int currentPoint = dataSet.length()-1;
		int numAttachedPoints = 1;
		attachedPoints.set(dataSet.length()-1);

		// Continue attaching points to the MST until all points are attached:
		while (numAttachedPoints < dataSet.length()) {
			int nearestMRDPoint = -1;
			double nearestMRDDistance = Double.MAX_VALUE;

			// Iterate through all unattached points, updating distances using the current point:
			for (int neighbor = 0; neighbor < dataSet.length(); neighbor++) {
				if (currentPoint == neighbor)
					continue;
				if (attachedPoints.get(neighbor) == true)
					continue;

				double distance = dataSet.computeDistance(currentPoint, neighbor);

				double mutualReachabiltiyDistance = distance;
				if (coreDistances[currentPoint][minPoints] > mutualReachabiltiyDistance)
					mutualReachabiltiyDistance = coreDistances[currentPoint][minPoints];
				if (coreDistances[neighbor][minPoints] > mutualReachabiltiyDistance)
					mutualReachabiltiyDistance = coreDistances[neighbor][minPoints];

				if (mutualReachabiltiyDistance < nearestMRDDistances[neighbor]) {
					nearestMRDDistances[neighbor] = mutualReachabiltiyDistance;
					nearestMRDNeighbors[neighbor] = currentPoint;
				}

				// Check if the unattached point being updated is the closest to the tree:
				if (nearestMRDDistances[neighbor] <= nearestMRDDistance) {
					nearestMRDDistance = nearestMRDDistances[neighbor];
					nearestMRDPoint = neighbor;
				}
			}

			// Attach the closest point found in this iteration to the tree:
			attachedPoints.set(nearestMRDPoint);
			numAttachedPoints++;
			currentPoint = nearestMRDPoint;
		}

		// Create an array for vertices in the tree that each point attached to:
		int[] otherVertexIndices = new int[dataSet.length()-1 + selfEdgeCapacity];
		for (int i = 0; i < dataSet.length()-1; i++) {
			otherVertexIndices[i] = i;
		}

		// If necessary, attach self edges:
		if (selfEdges) {
			for (int i = dataSet.length()-1; i < dataSet.length()*2-1; i++) {
				int vertex = i - (dataSet.length()-1);
				nearestMRDNeighbors[i] = vertex;
				otherVertexIndices[i] = vertex;
				nearestMRDDistances[i] = coreDistances[vertex][minPoints];
			}
		}

		return new UndirectedGraph(dataSet.length(), nearestMRDNeighbors, otherVertexIndices, nearestMRDDistances);
	}


	/**
	 * Computes the hierarchy and cluster tree from the minimum spanning tree, writing both to file, 
	 * and returns the cluster tree.  Additionally, the level at which each point becomes noise is
	 * computed.  Note that the minimum spanning tree may also have self edges (meaning it is not
	 * a true MST).
	 * @param mst A minimum spanning tree which has been sorted by edge weight in descending order
	 * @param minClusterSize The minimum number of points which a cluster needs to be a valid cluster
	 * @param compactHierarchy Indicates if hierarchy should include all levels or only levels at 
	 * which clusters first appear
	 * @param constraints An optional ArrayList of Constraints to calculate cluster constraint satisfaction
	 * @param hierarchyOutputFile The path to the hierarchy output file
	 * @param treeOutputFile The path to the cluster tree output file
	 * @param delimiter The delimiter to be used while writing both files
	 * @param pointNoiseLevels A double[] to be filled with the levels at which each point becomes noise
	 * @param pointLastClusters An int[] to be filled with the last label each point had before becoming noise
	 * @param outType The outputExtension to be generated by the HDBSCAN (i.e. .shm and/or (.csv and .vis)
	 * @param HMatrix The hierarchy matrix using the SHM structure.
	 * @param lineCount Integer used to count the lines written on the hierarchy file.
	 * @return The cluster tree
	 * @throws IOException If any errors occur opening or writing to the files
	 */
	public static ArrayList<Cluster> computeHierarchyAndClusterTree(UndirectedGraph mst,
			int minClusterSize, boolean compactHierarchy, ArrayList<Constraint> constraints, 
			String hierarchyOutputFile, String treeOutputFile, String delimiter, 
			double[] pointNoiseLevels, int[] pointLastClusters, String outType, HMatrix HMatrix, WrapInt lineCount) throws IOException {

		BufferedWriter hierarchyWriter = null;
		BufferedWriter treeWriter = null;
		BufferedWriter orderWriter = null;

		String orderOutputFile = treeOutputFile.replace("tree", "order");
		
		if (outType != HDBSCANStarRunner.SHM_OUT) {
			hierarchyWriter = new BufferedWriter(new FileWriter(hierarchyOutputFile), FILE_BUFFER_SIZE);
			treeWriter = new BufferedWriter(new FileWriter(treeOutputFile), FILE_BUFFER_SIZE);
			orderWriter = new BufferedWriter(new FileWriter(orderOutputFile), FILE_BUFFER_SIZE);
		}

		long hierarchyCharsWritten = 0;

		// This maps the last clusterID that was read on the file for each object.
		// These values should be inserted into the matrix only when the clusterID changes, reducing the number of insertions on ObjInstance.
		// On the .shm structure only the level where the clusters die is saved.

		double[] lastValuesKeys = new double[mst.getNumVertices()];
		int[] lastValuesValues = new int[mst.getNumVertices()];

		// If it outputs the .shm file, adding the objects to HMatrix structure.
		if(outType != HDBSCANStarRunner.VIS_OUT) {
			for(int id = 0; id < mst.getNumVertices(); id++) {
				HMatrix.add(new ObjInstance(id));

				// The first line of the hierarchy will surely be different than -1. 
				// But since the cluster will surely be 1, the density -1 will be overwritten.
				lastValuesKeys[id] = -1;
				lastValuesValues[id] = 1;
			}
		}

		lineCount.setValue(0); //Indicates the number of lines written into hierarchyFile.

		// The current edge being removed from the MST. The one with higher edge weight.
		int currentEdgeIndex = mst.getNumEdges() - 1;

		int nextClusterLabel = 2;
		boolean nextLevelSignificant = true;

		// The previous and current cluster numbers of each point in the data set.
		int[] previousClusterLabels = new int[mst.getNumVertices()];
		int[] currentClusterLabels  = new int[mst.getNumVertices()];

		for (int i = 0; i < currentClusterLabels.length; i++) {
			previousClusterLabels[i] = 1;
			currentClusterLabels[i]  = 1;
		}

		// A list of clusters in the cluster tree, with the 0th cluster (noise) null.
		ArrayList<Cluster> clusters = new ArrayList<Cluster>();
		clusters.add(null);
		clusters.add(new Cluster(1, null, Double.NaN, mst.getNumVertices(), null));

		// Calculate number of constraints satisfied for cluster 1.
		IntAVLTreeSet clusterOne = new IntAVLTreeSet();
		clusterOne.add(1);
		calculateNumConstraintsSatisfied(clusterOne, clusters, constraints, currentClusterLabels);		

		// Sets for the clusters and vertices that are affected by the edge(s) being removed.
		LinkedList<Integer> affectedClusters = new LinkedList<Integer>();
		LinkedList<Integer> affectedVertices = new LinkedList<Integer>();

		long total = 0;

		while(currentEdgeIndex >= 0) {
			// Retrieves the largest edge weight in the current tree.
			double currentEdgeWeight = mst.getEdgeWeightAtIndex(currentEdgeIndex);

			// ArrayList to store the clusters being created at this iteration.
			ArrayList<Cluster> newClusters = new ArrayList<Cluster>();

			// Loop to remove all edges tied with the current edge weight and keep track of affected clusters and vertices.
			while (currentEdgeIndex >= 0 && mst.getEdgeWeightAtIndex(currentEdgeIndex) == currentEdgeWeight) {
				int firstVertex  = mst.getFirstVertexAtIndex(currentEdgeIndex);
				int secondVertex = mst.getSecondVertexAtIndex(currentEdgeIndex);

				mst.getEdgeListForVertex(firstVertex).remove((Integer)secondVertex);
				mst.getEdgeListForVertex(secondVertex).remove((Integer)firstVertex);

				if (currentClusterLabels[firstVertex] == 0) {
					currentEdgeIndex--;
					continue;
				}

				affectedVertices.add(firstVertex);
				affectedVertices.add(secondVertex);
				affectedClusters.add(currentClusterLabels[firstVertex]);
				currentEdgeIndex--;
			}

			// Treats the case where edge removals do not affect any of the clusters.
			if (affectedClusters.isEmpty()) continue;

			// Check each affected cluster for a possible split:
			while (!affectedClusters.isEmpty()) {
				// Retrieves and removes the last affected cluster.
				int examinedClusterLabel = affectedClusters.poll();
				affectedClusters.remove((Integer)examinedClusterLabel);

				IntAVLTreeSet examinedVertices = new IntAVLTreeSet();

				// Get all affected vertices that are members of the cluster currently being examined:
				Iterator<Integer> vertexIterator = affectedVertices.iterator();
				while (vertexIterator.hasNext()) {
					int vertex = vertexIterator.next();

					if (currentClusterLabels[vertex] == examinedClusterLabel) {
						examinedVertices.add(vertex);
						vertexIterator.remove();
					}
				}

				IntOpenHashBigSet firstChildCluster = null;
				LinkedList<Integer> unexploredFirstChildClusterPoints = null;
				int numChildClusters = 0;

				/*
				 * Check if the cluster has split or shrunk by exploring the graph from each affected
				 * vertex.  If there are two or more valid child clusters (each has >= minClusterSize
				 * points), the cluster has split.
				 * Note that firstChildCluster will only be fully explored if there is a cluster
				 * split, otherwise, only spurious components are fully explored, in order to label 
				 * them noise.
				 */

				while (!examinedVertices.isEmpty()) {
					IntOpenHashBigSet constructingSubCluster = new IntOpenHashBigSet();

					LinkedList<Integer> unexploredSubClusterPoints = new LinkedList<Integer>();
					boolean anyEdges = false;
					boolean incrementedChildCount = false;

					int rootVertex = examinedVertices.lastInt();
					constructingSubCluster.add(rootVertex);					
					unexploredSubClusterPoints.add(rootVertex);
					examinedVertices.remove(rootVertex);

					// Explore this potential child cluster as long as there are unexplored points:
					while (!unexploredSubClusterPoints.isEmpty()) {
						int vertexToExplore = unexploredSubClusterPoints.poll();

						for (int neighbor : mst.getEdgeListForVertex(vertexToExplore)) {
							anyEdges = true;
							if (constructingSubCluster.add(neighbor)) {								
								unexploredSubClusterPoints.add(neighbor);
								examinedVertices.remove(neighbor);
							}	
						}

						// Check if this potential child cluster is a valid cluster:
						if (!incrementedChildCount && constructingSubCluster.size64() >= minClusterSize && anyEdges) {
							incrementedChildCount = true;
							numChildClusters++;

							// If this is the first valid child cluster, stop exploring it:
							if (firstChildCluster == null) {
								firstChildCluster = constructingSubCluster;
								unexploredFirstChildClusterPoints = unexploredSubClusterPoints;
								break;
							}	
						}
					}

					// If there could be a split, and this child cluster is valid:
					if (numChildClusters >= 2 && constructingSubCluster.size64() >= minClusterSize && anyEdges) {

						// Check this child cluster is not equal to the unexplored first child cluster:
						int firstChildClusterMember = firstChildCluster.iterator().next();
						if (constructingSubCluster.contains(firstChildClusterMember))
							numChildClusters--;

						// Otherwise, create a new cluster:
						else {
							Cluster newCluster = createNewCluster(constructingSubCluster, currentClusterLabels, clusters.get(examinedClusterLabel), nextClusterLabel, currentEdgeWeight);

							newClusters.add(newCluster);
							clusters.add(newCluster);
							nextClusterLabel++;
						}	
					}

					// If this child cluster is not valid cluster, assign it to noise:
					else if (constructingSubCluster.size64() < minClusterSize || !anyEdges) {
						createNewCluster(constructingSubCluster, currentClusterLabels, clusters.get(examinedClusterLabel), 0, currentEdgeWeight);

						for (int point : constructingSubCluster) {
							pointNoiseLevels[point] = currentEdgeWeight;
							pointLastClusters[point] = examinedClusterLabel;
						}
					}
				}

				// Finish exploring and cluster the first child cluster if there was a split and it was not already clustered:
				if (numChildClusters >= 2 && currentClusterLabels[firstChildCluster.iterator().next()] == examinedClusterLabel) {

					while (!unexploredFirstChildClusterPoints.isEmpty()) {
						int vertexToExplore = unexploredFirstChildClusterPoints.poll();

						for (int neighbor : mst.getEdgeListForVertex(vertexToExplore)) {
							if (firstChildCluster.add(neighbor))
								unexploredFirstChildClusterPoints.add(neighbor);
						}
					}

					Cluster newCluster = createNewCluster(firstChildCluster, currentClusterLabels, clusters.get(examinedClusterLabel), nextClusterLabel, currentEdgeWeight);

					newClusters.add(newCluster);
					clusters.add(newCluster);
					nextClusterLabel++;
				}
			}
			long b = System.currentTimeMillis();

			// Write out the current level of the hierarchy.
			if (!compactHierarchy || nextLevelSignificant || !newClusters.isEmpty()) {

				int outputLength = 0;

				if(!outType.equals(HDBSCANStarRunner.VIS_OUT))
				{
					//updating densities
					HMatrix.getDensities().add(currentEdgeWeight);
				}

				for (int i = 0; i < previousClusterLabels.length; i++) {

					//checking if the cluster changed
					int lastCluster = lastValuesValues[i];
					if(previousClusterLabels[i] != lastCluster)
					{	
						// Updating Object i
						HMatrix.matrix[i].put(lastValuesKeys[i], lastCluster);

						if(lastCluster > HMatrix.getMaxClusterID())
						{
							HMatrix.setMaxClusterID(lastCluster);
						}
					}

					// lastValues is updated anyway
					lastValuesKeys[i] = currentEdgeWeight;
					lastValuesValues[i] = previousClusterLabels[i];
				}

				// Update the order of the points to consider the current level just added.
				HMatrix.lexicographicSortIgnoreNoiseDynamic(lastValuesValues, currentEdgeWeight, hierarchyWriter);

				lineCount.inc();

				hierarchyCharsWritten+=outputLength;
			}

			total = total + (System.currentTimeMillis() - b);

			//Assign file offsets and calculate the number of constraints satisfied:
			IntAVLTreeSet newClusterLabels = new IntAVLTreeSet();

			for (Cluster newCluster : newClusters) {
				newCluster.setFileOffset(hierarchyCharsWritten);
				newClusterLabels.add(newCluster.getLabel());
			}

			if (!newClusterLabels.isEmpty())
				calculateNumConstraintsSatisfied(newClusterLabels, clusters, constraints, currentClusterLabels);

			for (int i = 0; i < previousClusterLabels.length; i++) {
				previousClusterLabels[i] = currentClusterLabels[i];
			}

			if (newClusters.isEmpty())
				nextLevelSignificant = false;
			else
				nextLevelSignificant = true;
		}

		//Write out the final level of the hierarchy (all points noise):

		if(!outType.equals(HDBSCANStarRunner.VIS_OUT))
		{

			HMatrix.getDensities().add(0.0);

			for(int i = 0; i < HMatrix.getMatrix().length; i++) {
				// Only updates if the clusterID isn't noise (i.e. different than 0)
				if(lastValuesValues[i] != 0) {
					HMatrix.matrix[i].put(lastValuesKeys[i], lastValuesValues[i]);
				}

				HMatrix.getMatrix()[i].put(0.0, 0);
			}


			HMatrix.lexicographicSortIgnoreNoiseDynamic(lastValuesValues, 0.0, hierarchyWriter);

			HMatrix.writeOrder(orderWriter);

		}

		// Write out the cluster tree:
		if(outType != HDBSCANStarRunner.SHM_OUT) {
			for (Cluster cluster : clusters) {
				if (cluster == null)
					continue;

				treeWriter.write(cluster.getLabel() + delimiter);
				treeWriter.write(cluster.getBirthLevel() + delimiter);
				treeWriter.write(cluster.getDeathLevel() + delimiter);
				treeWriter.write(cluster.getStability() + delimiter);

				if (constraints != null) {
					treeWriter.write((0.5 * cluster.getNumConstraintsSatisfied() / constraints.size()) + delimiter);
					treeWriter.write((0.5 * cluster.getPropagatedNumConstraintsSatisfied() / constraints.size()) + delimiter);
				}
				else {
					treeWriter.write(0 + delimiter);
					treeWriter.write(0 + delimiter);
				}

				treeWriter.write(cluster.getFileOffset() + delimiter);

				if (cluster.getParent() != null)
					treeWriter.write(cluster.getParent().getLabel() + "\n");
				else
					treeWriter.write(0 + "\n");
			}                       
		}

		if(hierarchyWriter != null) {
			hierarchyWriter.close();
			treeWriter.close();
			orderWriter.close();
		}
		
		appendFiles(orderOutputFile, hierarchyOutputFile);
		
		return clusters;
	}

	public static void appendFiles(String orderOutputFile, String hierarchyOutputFile) throws IOException {
		// Appends file "tmpFile" to the end of "permFile"
		// Code adapted from:  https://www.daniweb.com/software-development/java/threads/44508/appending-two-java-text-files

		try {
			// create a writer for permFile
			BufferedWriter out = new BufferedWriter(new FileWriter(orderOutputFile, true));

			// create a reader for tmpFile
			BufferedReader in = new BufferedReader(new FileReader(hierarchyOutputFile));
			
			String str;
			
			while ((str = in.readLine()) != null) {
				out.write(str);
				out.newLine();
			}
			
			in.close();
			out.close();
			
		} catch (IOException e) {
		}
		
		File order = new File(orderOutputFile);
        File hierarchy = new File(hierarchyOutputFile);
        
        order.renameTo(hierarchy);
	}

	/**
	 * Propagates constraint satisfaction, stability, and lowest child death level from each child
	 * cluster to each parent cluster in the tree.  This method must be called before calling
	 * findProminentClusters() or calculateOutlierScores().
	 * @param clusters A list of Clusters forming a cluster tree
	 * @return true if there are any clusters with infinite stability, false otherwise
	 */
	public static boolean propagateTree(ArrayList<Cluster> clusters) {
		TreeMap<Integer, Cluster> clustersToExamine = new TreeMap<Integer, Cluster>();
		BitSet addedToExaminationList = new BitSet(clusters.size());
		boolean infiniteStability = false;

		//Find all leaf clusters in the cluster tree:
		for (Cluster cluster : clusters) {
			if (cluster != null && !cluster.hasChildren()) {
				clustersToExamine.put(cluster.getLabel(), cluster);
				addedToExaminationList.set(cluster.getLabel());
			}	
		}

		//Iterate through every cluster, propagating stability from children to parents:
		while (!clustersToExamine.isEmpty()) {
			Cluster currentCluster = clustersToExamine.pollLastEntry().getValue();
			currentCluster.propagate();

			if (currentCluster.getStability() == Double.POSITIVE_INFINITY)
				infiniteStability = true;

			if (currentCluster.getParent() != null) {
				Cluster parent = currentCluster.getParent();

				if (!addedToExaminationList.get(parent.getLabel())) {
					clustersToExamine.put(parent.getLabel(), parent);
					addedToExaminationList.set(parent.getLabel());
				}	
			}
		}

		if (infiniteStability)
			System.out.println(WARNING_MESSAGE);

		return infiniteStability;
	}


	/**
	 * Produces a flat clustering result using constraint satisfaction and cluster stability, and 
	 * returns an array of labels.  propagateTree() must be called before calling this method.
	 * @param clusters A list of Clusters forming a cluster tree which has already been propagated
	 * @param hierarchyFile The path to the hierarchy input file
	 * @param flatOutputFile The path to the flat clustering output file
	 * @param delimiter The delimiter for both files
	 * @param numPoints The number of points in the original data set
	 * @param infiniteStability true if there are any clusters with infinite stability, false otherwise
	 * @return An array of labels for the flat clustering result
	 * @throws IOException If any errors occur opening, reading, or writing to the files
	 * @throws NumberFormatException If illegal number values are found in the hierarchyFile
	 * 
	 * @param rootTree 01/08/2016. included on function to search for the cluster solution on a subtree of the hierarchy. 
	 */
	public static int[] findProminentClusters(ArrayList<Cluster> clusters, int rootTree, String hierarchyFile,
			String flatOutputFile, String delimiter, int numPoints, boolean infiniteStability) throws IOException, NumberFormatException {

		//Take the list of propagated clusters from the root cluster:
		ArrayList<Cluster> solution = clusters.get(rootTree).getPropagatedDescendants();

		BufferedReader reader = new BufferedReader(new FileReader(hierarchyFile));
		int[] flatPartitioning = new int[numPoints];
		long currentOffset = 0;

		//Store all the file offsets at which to find the birth points for the flat clustering:
		TreeMap<Long, ArrayList<Integer>> significantFileOffsets = new TreeMap<Long, ArrayList<Integer>>();

		for (Cluster cluster: solution) {
			ArrayList<Integer> clusterList = significantFileOffsets.get(cluster.getFileOffset());

			if (clusterList == null) {
				clusterList = new ArrayList<Integer>();
				significantFileOffsets.put(cluster.getFileOffset(), clusterList);
			}

			clusterList.add(cluster.getLabel());
		}

		//Go through the hierarchy file, setting labels for the flat clustering:
		while (!significantFileOffsets.isEmpty()) {
			Map.Entry<Long, ArrayList<Integer>> entry = significantFileOffsets.pollFirstEntry();
			ArrayList<Integer> clusterList = entry.getValue();
			Long offset = entry.getKey();

			reader.skip(offset - currentOffset);
			String line = reader.readLine();

			currentOffset = offset + line.length() + 1;
			String[] lineContents = line.split(delimiter);

			for (int i = 1; i < lineContents.length; i++) {
				int label = Integer.parseInt(lineContents[i]);
				if (clusterList.contains(label))
					flatPartitioning[i-1] = label;
			}
		}

		reader.close();

		//Output the flat clustering result:
		BufferedWriter writer = new BufferedWriter(new FileWriter(flatOutputFile), FILE_BUFFER_SIZE);
		if (infiniteStability)
			writer.write(WARNING_MESSAGE + "\n");

		for (int i = 0; i < flatPartitioning.length-1; i++) {
			writer.write(flatPartitioning[i] + delimiter);
		}
		writer.write(flatPartitioning[flatPartitioning.length-1] + "\n");
		writer.close();

		return flatPartitioning;
	}

	public static int[] findProminentClusters(ArrayList<Cluster> clusters, String hierarchyFile,
			String flatOutputFile, String delimiter, int numPoints, boolean infiniteStability) throws IOException, NumberFormatException {
		return findProminentClusters(clusters, 0, hierarchyFile, flatOutputFile, delimiter, numPoints, infiniteStability);
	}


	/**
	 * Produces a flat clustering result using constraint satisfaction and cluster stability, and 
	 * returns an array of labels.  propagateTree() must be called before calling this method.
	 * @param clusters A list of Clusters forming a cluster tree which has already been propagated
	 * @param matrix The hierarchy matrix.
	 * @param flatOutputFile The path to the flat clustering output file
	 * @return An array of labels for the flat clustering result
	 * @throws IOException If any errors occur opening, reading, or writing to the files
	 * @throws NumberFormatException If illegal number values are found in the hierarchyFile
	 * @author Fernando S. de Aguiar Neto.
	 * 
	 * @param rootTree 01/08/2016. included on function to search for the cluster solution on a subtree of the hierarchy.
	 */
	public static int[] findProminentClustersSHM(ArrayList<Cluster> clusters, HMatrix matrix) throws NumberFormatException {

		int numPoints = matrix.getMatrix().length;

		//Take the list of propagated clusters from the root cluster:
		ArrayList<Cluster> solution = clusters.get(1).getPropagatedDescendants();

		int[] flatPartitioning = new int[numPoints];

		//Store all the densities where the significant clusters are born:
		TreeMap<Double, IntOpenHashBigSet> significantLevels = new TreeMap<Double, IntOpenHashBigSet>();

		HashMap<Integer, Cluster> elements = new HashMap<Integer, Cluster>();

		for (Cluster cluster: solution) {
			long lineIdx = matrix.getDensities().indexOf(clusters.get(cluster.getLabel()).getBirthLevel()) +1;
			double firstLine = matrix.getDensity(lineIdx);

			IntOpenHashBigSet clusterList = significantLevels.get(firstLine);

			if (clusterList == null) {
				clusterList = new IntOpenHashBigSet();
				significantLevels.put(firstLine, clusterList);
			}

			clusterList.add(cluster.getLabel());
			elements.put(cluster.getLabel(), cluster);
		}

		for (int i : elements.keySet()) {
			for (int j : elements.get(i).getObjects()) {
				flatPartitioning[j] = i;
			}
		}

		return flatPartitioning;
	}


	/**
	 * Produces the outlier score for each point in the data set, and returns a sorted list of outlier
	 * scores.  propagateTree() must be called before calling this method.
	 * @param clusters A list of Clusters forming a cluster tree which has already been propagated
	 * @param pointNoiseLevels A double[] with the levels at which each point became noise
	 * @param pointLastClusters An int[] with the last label each point had before becoming noise
	 * @param coreDistances An array of core distances for each data point
	 * @param outlierScoresOutputFile The path to the outlier scores output file
	 * @param delimiter The delimiter for the output file
	 * @param infiniteStability true if there are any clusters with infinite stability, false otherwise
	 * @param outType indicates if the .shm or the .vis file must be generated
	 * @param HMatrix Hierarchy matrix using the shm structure
	 * @return An ArrayList of OutlierScores, sorted in descending order
	 * @throws IOException If any errors occur opening or writing to the output file
	 */
	public static ArrayList<OutlierScore> calculateOutlierScores(ArrayList<Cluster> clusters, 
			double[] pointNoiseLevels, int[] pointLastClusters, double[][] coreDistances, int minPoints,
			String outlierScoresOutputFile, String delimiter, boolean infiniteStability, String outType, HMatrix HMatrix) throws IOException {

		int numPoints = pointNoiseLevels.length;
		ArrayList<OutlierScore> outlierScores = new ArrayList<OutlierScore>(numPoints);

		//Iterate through each point, calculating its outlier score:
		for (int i = 0; i < numPoints; i++) {
			double epsilon_max = clusters.get(pointLastClusters[i]).getPropagatedLowestChildDeathLevel();
			double epsilon = pointNoiseLevels[i];

			double score = 0;
			if (epsilon != 0)
				score = 1-(epsilon_max/epsilon);

			outlierScores.add(new OutlierScore(score, coreDistances[i][minPoints-1], i));
			if(!outType.equals(HDBSCANStarRunner.VIS_OUT)) {
				HMatrix.getObjInstanceByID(i).setOutlierScore(score);
				HMatrix.getObjInstanceByID(i).setCoreDistance(coreDistances[i][minPoints-1]);
			}
		}

		//Sort the outlier scores:
		Collections.sort(outlierScores);

		if(!outType.equals(HDBSCANStarRunner.SHM_OUT)) {
			//Output the outlier scores:
			BufferedWriter writer = new BufferedWriter(new FileWriter(outlierScoresOutputFile), FILE_BUFFER_SIZE);
			if (infiniteStability)
				writer.write(WARNING_MESSAGE + "\n");

			for (OutlierScore outlierScore : outlierScores) {
				writer.write(outlierScore.getScore() + delimiter + outlierScore.getId() + "\n");
			}
			writer.close();
		}

		return outlierScores;
	}

	/**
	 * Calculates the constraints satisfied for all clusters using the entire matrix.
	 * @param matrix The hierarchy matrix.
	 * @param clusters ArrayList of Clusters
	 * @param constraints an array of current cluster labels for points
	 */
	public static void calculateAllNumContraintsSatisfied(HMatrix matrix, ArrayList<Cluster> clusters, ArrayList<Constraint> constraints)
	{
		if (constraints == null)
			return;

		//The current cluster numbers of each point in the data set:
		int[] currentClusterLabels = new int[matrix.getMatrix().length];
		for (int i = 0; i < currentClusterLabels.length; i++) {
			currentClusterLabels[i] = 1;
		}

		//Calculate number of constraints satisfied for cluster 1:
		IntAVLTreeSet clusterOne = new IntAVLTreeSet();
		clusterOne.add(1);
		calculateNumConstraintsSatisfied(clusterOne, clusters, constraints, currentClusterLabels);

		//Finding split levels
		TreeMap<Double, IntAVLTreeSet> splitLevels = new TreeMap<Double, IntAVLTreeSet>();

		for (int i = 2; i < clusters.size(); i++) {
			long lineIdx = matrix.getDensities().indexOf(clusters.get(i).getBirthLevel()) +1;
			Double firstLine = matrix.getDensity(lineIdx);

			IntAVLTreeSet clusterList = splitLevels.get(firstLine);

			if (clusterList == null) {
				clusterList = new IntAVLTreeSet();
				splitLevels.put(firstLine, clusterList);
			}

			clusterList.add(clusters.get(i).getLabel());
		}

		//Given the split points, calculateNumConstraintsSatisfied.
		for(Double level : splitLevels.keySet()) {
			for (int i = 0; i < currentClusterLabels.length; i++) {
				currentClusterLabels[i] = matrix.getObjInstanceByID(i).getClusterID(level);
			}                

			ArrayList<Cluster> parents = new ArrayList<Cluster>();
			for (int label : splitLevels.get(level)) {
				Cluster parent = clusters.get(label).getParent();
				if (parent != null && !parents.contains(parent))
					parents.add(parent);
			}

			for (Constraint constraint : constraints) {
				int labelA = currentClusterLabels[constraint.getPointA()];
				int labelB = currentClusterLabels[constraint.getPointB()];

				if (constraint.getType() == CONSTRAINT_TYPE.MUST_LINK && labelA == labelB) {
					if (splitLevels.get(level).contains(labelA))
						clusters.get(labelA).addConstraintsSatisfied(2);
				}

				else if (constraint.getType() == CONSTRAINT_TYPE.CANNOT_LINK && (labelA != labelB || labelA == 0)) {
					if (labelA != 0 && splitLevels.get(level).contains(labelA))
						clusters.get(labelA).addConstraintsSatisfied(1);
					if (labelB != 0 && splitLevels.get(level).contains(labelB))
						clusters.get(labelB).addConstraintsSatisfied(1);

					if (labelA == 0) {
						for (Cluster parent : parents) {
							if (parent.getObjects().contains(constraint.getPointA())) {
								parent.addVirtualChildConstraintsSatisfied(1);
								break;
							}
						}
					}

					if (labelB == 0) {
						for (Cluster parent : parents) {
							if (parent.getObjects().contains(constraint.getPointB())) {
								parent.addVirtualChildConstraintsSatisfied(1);
								break;
							}
						}
					}
				}
			}
		}
	}

	// ------------------------------ PRIVATE METHODS ------------------------------

	/**
	 * Removes the set of points from their parent Cluster, and creates a new Cluster, provided the
	 * clusterId is not 0 (noise).
	 * @param points The set of points to be in the new Cluster
	 * @param clusterLabels An array of cluster labels, which will be modified
	 * @param parentCluster The parent Cluster of the new Cluster being created
	 * @param clusterLabel The label of the new Cluster 
	 * @param edgeWeight The edge weight at which to remove the points from their previous Cluster
	 * @return The new Cluster, or null if the clusterId was 0
	 */
	private static Cluster createNewCluster(IntOpenHashBigSet points, int[] clusterLabels, Cluster parentCluster, int clusterLabel, double edgeWeight) {

		for (int point : points) {
			clusterLabels[point] = clusterLabel;
		}

		parentCluster.detachPoints(points.size64(), edgeWeight);

		if (clusterLabel != 0) {
			Cluster cluster = new Cluster(clusterLabel, parentCluster, edgeWeight, points.size64(), points);
			cluster.setObjects(points);
			return cluster;
		} else {
			parentCluster.addPointsToVirtualChildCluster(points);
			return null;
		}
	}


	/**
	 * Calculates the number of constraints satisfied by the new clusters and virtual children of the
	 * parents of the new clusters.
	 * @param newClusterLabels Labels of new clusters
	 * @param clusters An ArrayList of clusters
	 * @param constraints An ArrayList of constraints
	 * @param clusterLabels an array of current cluster labels for points
	 */
	private static void calculateNumConstraintsSatisfied(IntAVLTreeSet newClusterLabels, 
			ArrayList<Cluster> clusters, ArrayList<Constraint> constraints, int[] clusterLabels) {

		if (constraints == null)
			return;

		ArrayList<Cluster> parents = new ArrayList<Cluster>();
		for (int label : newClusterLabels) {
			Cluster parent = clusters.get(label).getParent();
			if (parent != null && !parents.contains(parent))
				parents.add(parent);
		}

		for (Constraint constraint : constraints) {
			int labelA = clusterLabels[constraint.getPointA()];
			int labelB = clusterLabels[constraint.getPointB()];

			if (constraint.getType() == CONSTRAINT_TYPE.MUST_LINK && labelA == labelB) {
				if (newClusterLabels.contains(labelA))
					clusters.get(labelA).addConstraintsSatisfied(2);
			}

			else if (constraint.getType() == CONSTRAINT_TYPE.CANNOT_LINK && (labelA != labelB || labelA == 0)) {
				if (labelA != 0 && newClusterLabels.contains(labelA))
					clusters.get(labelA).addConstraintsSatisfied(1);
				if (labelB != 0 && newClusterLabels.contains(labelB))
					clusters.get(labelB).addConstraintsSatisfied(1);

				if (labelA == 0) {
					for (Cluster parent : parents) {
						if (parent.virtualChildClusterContaintsPoint(constraint.getPointA())) {
							parent.addVirtualChildConstraintsSatisfied(1);
							break;
						}
					}
				}

				if (labelB == 0) {
					for (Cluster parent : parents) {
						if (parent.virtualChildClusterContaintsPoint(constraint.getPointB())) {
							parent.addVirtualChildConstraintsSatisfied(1);
							break;
						}
					}
				}
			}
		}

		for (Cluster parent : parents) {
			parent.releaseVirtualChildCluster();
		}
	}
	// ------------------------------ GETTERS & SETTERS ------------------------------
}