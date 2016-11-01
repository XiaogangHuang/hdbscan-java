package ca.ualberta.cs.hdbscanstar;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Stack;

import ca.ualberta.cs.distance.DistanceCalculator;
import ca.ualberta.cs.distance.EuclideanDistance;
import ca.ualberta.cs.util.FairSplitTree;
import ca.ualberta.cs.util.Pair;

import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.doubles.DoubleBigArrayBigList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

public class RelativeNeighborhoodGraph extends Graph {
	public static Double[][] dataSet;
	public static double[][] coreDistances;
	public static int k;

	public static final String WS = "WS";
	public static final String SS = "SS";

	public int numOfEdgesRNG;

	public BigList<Integer> edgesA;
	public BigList<Integer> edgesB;
	public BigList<Double> weights;

	public Integer[] sortedEdges;

	public BigList<Integer> A;
	public BigList<Integer> B;
	public BigList<FairSplitTree> C;
	public BigList<Double> W;

	public int x = 0;

	public Int2ObjectOpenHashMap<BigList<Integer>> pairs;

	/**
	 * Relative Neighborhood Graph naive constructor. Takes O(n³) time.
	 * 
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunction
	 * @param k
	 */
	public RelativeNeighborhoodGraph(Double[][] dataSet, double[][] coreDistances, DistanceCalculator distanceFunction, int k){
		A = new IntBigArrayBigList();
		B = new IntBigArrayBigList();
		W = new DoubleBigArrayBigList();

		RelativeNeighborhoodGraph.dataSet = dataSet;
		RelativeNeighborhoodGraph.coreDistances = coreDistances;
		RelativeNeighborhoodGraph.k = k;

		pairs = new Int2ObjectOpenHashMap<BigList<Integer>>();

		for (int i = 0; i < dataSet.length; i++) {
			for (int j = i + 1; j < dataSet.length; j++) {

				if (neighbors(dataSet, coreDistances, i, j, k)) {
					A.add(i);
					B.add(j);
					W.add(mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, i, j, k));
				}
			}
		}

		numOfEdgesRNG = A.size();

		edgesA =  A;
		edgesB =  B;
		weights = W;

		sortedEdges = new Integer[numOfEdgesRNG];

		for (int i = 0; i < numOfEdgesRNG; i++) {
			sortedEdges[i] = i;
		}

		// Cleaning no longer needed structures.
		A = null;
		B = null;
		W = null;
	}


	/**
	 * Relative Neighborhood Graph constructor based on the Well-Separated Pair Decomposition.
	 * 
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunctionimport java.util.List;
	 * @param k
	 * @param filter
	 * @param s
	 * @param method
	 */
	public RelativeNeighborhoodGraph(Double[][] dataSet, double[][] coreDistances, DistanceCalculator distanceFunction, int k, boolean filter, double s, String method) {

		// Initializes attributes to store the RNG edges initially.
		A = new IntBigArrayBigList();
		B = new IntBigArrayBigList();
		C = new ObjectBigArrayBigList<FairSplitTree>();
		W = new DoubleBigArrayBigList();

		pairs = new Int2ObjectOpenHashMap<BigList<Integer>>();

		RelativeNeighborhoodGraph.dataSet = dataSet;
		RelativeNeighborhoodGraph.coreDistances = coreDistances;
		RelativeNeighborhoodGraph.k = k;

		// Builds the Fair Split Tree T from dataSet.
		FairSplitTree T = FairSplitTree.build(dataSet);

		// Finds all the Well-separated Pairs from T.
		findWSPD(T, s, method);

//		System.out.println(x);

		boolean naiveFilter = false;

		BigList<Integer> finalA = new IntBigArrayBigList();
		BigList<Integer> finalB = new IntBigArrayBigList();
		BigList<FairSplitTree> finalC = new ObjectBigArrayBigList<FairSplitTree>();
		BigList<Double> finalW = new DoubleBigArrayBigList();

		if (naiveFilter) {

			for (int e = 0; e < A.size(); e++) {
				if (neighbors(dataSet, coreDistances, A.get(e), B.get(e), k)) {
					finalA.add(A.get(e));
					finalB.add(B.get(e));
					finalW.add(W.get(e));
					finalC.add(C.get(e));
				}
			}
		}

		if (filter) {

			// boolean variable that tells whether the end points of this edge are neighbors or not.
			boolean neighbors;

			for (int e = 0; e < A.size(); e++) {

				neighbors = true;

				double cdA = coreDistances[A.get(e)][k-1];
				double cdB = coreDistances[B.get(e)][k-1];

				double w = W.get(e);

				// In this first case, we check if the points are in each other's k-neighborhood.
				if (w == Math.max(cdA, cdB)) {

					int[] kNN;

					if (cdA > cdB) {
						kNN = IncrementalHDBSCANStar.kNN[A.get(e)];
					} else {
						kNN = IncrementalHDBSCANStar.kNN[B.get(e)];
					}

					for (int i = 0; i < kNN.length; i++) {

						if (coreDistances[kNN[i]][k-1] > w) break;

						double dac = mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, A.get(e), kNN[i], k);
						double dbc = mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, B.get(e), kNN[i], k);

						if (w > Math.max(dac, dbc)) {
							neighbors = false;
							break;
						}
					}

					if (neighbors) {
						finalA.add(A.get(e));
						finalB.add(B.get(e));
						finalW.add(W.get(e));
						finalC.add(C.get(e));
					}

					continue;
				}

				if (neighbors(dataSet, coreDistances, A.get(e), B.get(e), k)) {
					finalA.add(A.get(e));
					finalB.add(B.get(e));
					finalW.add(W.get(e));
					finalC.add(C.get(e));
				}
			}
		}

		if (filter) {
			numOfEdgesRNG = finalW.size();			
		} else {
			numOfEdgesRNG = W.size();
		}

		sortedEdges = new Integer[numOfEdgesRNG];

		for (int i = 0; i < numOfEdgesRNG; i++) {
			sortedEdges[i] = i;
		}

		if (filter) {
			edgesA = finalA;
			edgesB = finalB;
			weights = finalW;			
		} else {
			edgesA = A;
			edgesB = B;
			weights = W;
		}

		// Cleaning no longer needed structures.
		A = null;
		B = null;
		C = null;
		W = null;

		finalA = null;
		finalB = null;
		finalC = null;
		finalW = null;		
	}

	public void SBCN(FairSplitTree T1, FairSplitTree T2, Double[][] dataSet, double[][] coreDistances, DistanceCalculator distanceFunction, int k) {
		double d;

		HashMap<Pair, FairSplitTree> tmp  = new HashMap<Pair, FairSplitTree>();
		HashMap<Pair, FairSplitTree> tmp2  = new HashMap<Pair, FairSplitTree>();

		double min = Double.MAX_VALUE;
		BigList<Integer> tempA = new IntBigArrayBigList();
		BigList<Integer> tempB = new IntBigArrayBigList();

		for (Integer p1 : T1.retrieve()) {

			for (Integer p2 : T2.retrieve()) {

				d = mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, p1, p2, k);
				
				if (d < min) {
					tempA.clear();
					tempB.clear();
				}

				if (d <= min) {
					min = d;
					tempA.add(p1);
					tempB.add(p2);
				}
			}
			
			for (int i = 0; i < tempA.size(); i++) {
				Pair p;

				if (tempA.get(i) < tempB.get(i)) {
					p = new Pair(tempA.get(i), tempB.get(i));
				} else {
					p = new Pair(tempB.get(i), tempA.get(i));
				}

				if (!tmp.containsKey(p)) tmp.put(p, FairSplitTree.parent(T1, T2));
			}
			
			min = Double.MAX_VALUE;
		}
		
		tempA = new IntBigArrayBigList();
		tempB = new IntBigArrayBigList();

		for (Integer p2 : T2.retrieve()) {

			for (Integer p1 : T1.retrieve()) {

				d = mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, p1, p2, k);
				
				if (d < min) {
					tempA.clear();
					tempB.clear();
				}

				if (d <= min) {
					min = d;
					tempA.add(p2);
					tempB.add(p1);
				}
			}
			
			for (int i = 0; i < tempA.size(); i++) {
				Pair p;

				if (tempA.get(i) < tempB.get(i)) {
					p = new Pair(tempA.get(i), tempB.get(i));
				} else {
					p = new Pair(tempB.get(i), tempA.get(i));
				}

				if (tmp.containsKey(p)) tmp2.put(p, FairSplitTree.parent(T1, T2));
			}
			
			min = Double.MAX_VALUE;
		}

		for (Pair p : tmp2.keySet()) {
			A.add(p.a);
			B.add(p.b);

			C.add(tmp.get(p));

			W.add(mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, p.a, p.b, k));
		}

		tempA = null;
		tempB = null;
		tmp = null;
	}

	public boolean neighbors(Double[][] dataSet, double[][] coreDistances, int i, int j, int k){
		double dij = mutualReachabilityDistance(dataSet, coreDistances, new EuclideanDistance(), i, j, k);

		for (int m = 0; m < coreDistances.length; m++) {
			double dim = mutualReachabilityDistance(dataSet, coreDistances, new EuclideanDistance(), i, m, k);
			double djm = mutualReachabilityDistance(dataSet, coreDistances, new EuclideanDistance(), j, m, k);

			if (dij > Math.max(dim, djm)) {
				return false;
			}
		}

		return true;
	}

	public void findWSPD(FairSplitTree T, double s, String method) {
		Stack<Integer> stack = new Stack<Integer>();

		stack.add(T.id);

		while (!stack.isEmpty()) {
			int i = stack.pop();

			FairSplitTree current = FairSplitTree.root.get(i);

			if (!current.getLeft().isLeaf()) {
				stack.add(current.left());
			}

			if (!current.getRight().isLeaf()) {
				stack.add(current.right());
			}

			findPairs(current.getLeft(), current.getRight(), s, method);
		}

		stack = null;
	}

	public void findPairs(FairSplitTree T1, FairSplitTree T2, double s, String method) {


		if (separated(T1, T2, s, method)) {
//			int a = 2860;
//			int b = 3718;
//			if ((T1.retrieve().contains(a) && T2.retrieve().contains(b)) || (T1.retrieve().contains(b) && T2.retrieve().contains(a))) {
//				System.out.println("--------------------------------------------------------");
//				System.out.println("T1: " + T1.retrieve());
//				System.out.println("T1 diameter: " + T1.diameter());
//				System.out.println("T1 max CD: " + T1.getMaxCD());
//				System.out.println("T2: " + T2.retrieve());
//				System.out.println("T2 diameter: " + T2.diameter());
//				System.out.println("T2 max CD: " + T2.getMaxCD());
//				System.out.println("distance(T1, T2): " + FairSplitTree.circleDistance(T1, T2));
//				System.out.println();
//				for (Integer integer : T1.retrieve()) {
//					for (int i = 0; i < dataSet[0].length; i++) {
//						System.out.print(dataSet[integer][i] + " ");
//					}
//					System.out.println();
//				}
//				for (Integer integer : T2.retrieve()) {
//					for (int i = 0; i < dataSet[0].length; i++) {
//						System.out.print(dataSet[integer][i] + " ");
//					}
//					System.out.println();
//				}
//				
//				System.out.println("--------------------------------------------------------");
//			}

			if (rn(T1, T2)) {
				x++;
				SBCN(T1, T2, dataSet, coreDistances, new EuclideanDistance(), k);				
			}
		} else {
			if (T1.diameterMRD() <= T2.diameterMRD()) {
				findPairs(T1, T2.getLeft() , s, method);
				findPairs(T1, T2.getRight(), s, method);
			} else {
				findPairs(T1.getLeft(),  T2, s, method);
				findPairs(T1.getRight(), T2, s, method);
			}
		}
	}


	/**
	 * @param T1
	 * @param T2
	 * @param s
	 * @return
	 */
	public static boolean separated(FairSplitTree T1, FairSplitTree T2, double s) {
		return ws(T1, T2, s);
	}

	/**
	 * @param T1
	 * @param T2
	 * @param s
	 * @param method
	 * @return
	 */
	public static boolean separated(FairSplitTree T1, FairSplitTree T2, double s, String method) {

		if (T1.id == T2.id) {
			return false;
		}

		if (method == SS) {
			return ss(T1, T2, s);
		} else {
			return ws(T1, T2, s);
		}		
	}

	/**
	 * Receives two Fair Split Trees T1 and T2 and returns whether they are
	 * well separated or not.
	 * 
	 * @param T1 FairSplitTree #1.
	 * @param T2 FairSplitTree #2.
	 * @param s Separation factor.
	 * @return true if T1 and T2 are well separated, false otherwise. 
	 */
	public static boolean ws(FairSplitTree T1, FairSplitTree T2, double s){

		double d = FairSplitTree.circleDistance(T1, T2);

		if (d >= s*Math.max(T1.diameterMRD(), T2.diameterMRD())) {
			return true;
		} else {
			return false;
		}
	}


	/** Method to check whether two FairSplitTrees might emit a RNG edge.
	 * It does not work currently, the core-distance of the points in each subtree would have to be checked.
	 * @param T1
	 * @param T2
	 * @return
	 */
	public static boolean rn(FairSplitTree T1, FairSplitTree T2) {
		double r = FairSplitTree.circleDistance(T1, T2)/2;

		Double[] q = new Double[T1.center().length];

		for (int i = 0; i < q.length; i++) {
			q[i] = (T1.center()[i] + T2.center()[i])/2;
		}

		double dab = Math.max(2*r, Math.max(T1.getMaxCD(), T2.getMaxCD()));

		BigList<Integer> result = FairSplitTree.rangeSearch(FairSplitTree.parent(T1, T2), q, r, new IntBigArrayBigList());

//		if (T1.retrieve().contains(4927) && T2.retrieve().contains(3766)) {
//			System.out.println("--------------------------------------------------------");
//			System.out.println("T1: " + T1.retrieve());
//			System.out.println("T1 diameter: " + T1.diameter());
//			System.out.println("T1 max CD: " + T1.getMaxCD());
//			System.out.println("T2: " + T2.retrieve());
//			System.out.println("T2 diameter: " + T2.diameter());
//			System.out.println("T2 max CD: " + T2.getMaxCD());
//			System.out.println("distance(T1, T2): " + 2*r);
//			System.out.println("Size: " + result.size());
//			System.out.print("Result: ");
//			for (Integer t : result) {
//				System.out.print(t + " ");
//			}
//			System.out.println();
//			for (Integer integer : T1.retrieve()) {
//				for (int i = 0; i < dataSet[0].length; i++) {
//					System.out.print(dataSet[integer][i] + " ");
//				}
//				System.out.println();
//			}
//
//			for (Integer integer : T2.retrieve()) {
//				for (int i = 0; i < dataSet[0].length; i++) {
//					System.out.print(dataSet[integer][i] + " ");
//				}
//				System.out.println();
//			}
//			
//			for (int i = 0; i < dataSet[0].length; i++) {
//				System.out.print(dataSet[5174][i] + " ");
//			}
//			
//			System.out.println();
//
//			System.out.println();
//			System.out.println("--------------------------------------------------------");
//		}

		if (result.isEmpty()) {
			return true;
		} else {
			for (Integer i : result) {
//				if (!T1.retrieve().contains(i) && !T2.retrieve().contains(i)) {

					double dac = Math.max(FairSplitTree.circleDistance(dataSet[i], T1) + T1.diameter(), Math.max(coreDistances[i][k-1], T1.getMaxCD()));
					double dbc = Math.max(FairSplitTree.circleDistance(dataSet[i], T2) + T2.diameter(), Math.max(coreDistances[i][k-1], T2.getMaxCD()));

					if (dab > Math.max(dac, dbc)) {
//						if (T1.retrieve().contains(4927) && T2.retrieve().contains(3766) && i == 5174) {
//							System.out.println("distances: ");
//							System.out.println("A - B: " + dab);
//							System.out.println("A - C: " + dac);
//							System.out.println("B - C: " + dbc);
//							
//						}
						return false;
					}
//				}
			}

			return true;
		}
	}

	/**
	 * Receives two Fair Split Trees T1 and T2 and returns whether they are
	 * semi-separated or not.
	 * 
	 * @param T1 FairSplitTree #1.
	 * @param T2 FairSplitTree #2.
	 * @param s Separation factor.
	 * @return true if T1 and T2 are well separated, false otherwise. 
	 */
	public static boolean ss(FairSplitTree T1, FairSplitTree T2, double s){

		double d = FairSplitTree.circleDistance(T1, T2);

		if (d >= s*Math.min(T1.diameterMRD(), T2.diameterMRD())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunction
	 * @param i
	 * @param j
	 * @param k
	 * @return
	 */
	public static double mutualReachabilityDistance(Double[][] dataSet, double[][] coreDistances, DistanceCalculator distanceFunction, int i, int j, int k) {
		double mutualReachabiltiyDistance = distanceFunction.computeDistance(dataSet[i], dataSet[j]);

		if (coreDistances[i][k - 1] > mutualReachabiltiyDistance)
			mutualReachabiltiyDistance = coreDistances[i][k - 1];

		if (coreDistances[j][k - 1] > mutualReachabiltiyDistance)
			mutualReachabiltiyDistance = coreDistances[j][k - 1];

		return mutualReachabiltiyDistance;
	}

	/**
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunction
	 * @param k
	 */
	public void updateWeights(Double[][] dataSet, double[][] coreDistances, DistanceCalculator distanceFunction, int k) {
		for (int i = 0; i < numOfEdgesRNG; i++) {
			this.weights.set(sortedEdges[i], mutualReachabilityDistance(dataSet, coreDistances, distanceFunction, this.edgesA.get(sortedEdges[i]), this.edgesB.get(sortedEdges[i]), k));
		}
	}

	public Integer[] timSort(){
		Comparator<Integer> c = new Comparator<Integer>() {

			@Override
			public int compare(Integer o1, Integer o2) {
				if (weights.get(o1) < weights.get(o2)) {
					return -1;
				}
				if (weights.get(o1) > weights.get(o2)) {					
					return 1;
				}
				return 0;
			}
		};

		TimSort.sort(sortedEdges, c);

		return sortedEdges;
	}
}
