package ca.ualberta.cs.hdbscanstar;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Stack;

import ca.ualberta.cs.distance.DistanceCalculator;
import ca.ualberta.cs.distance.EuclideanDistance;
import ca.ualberta.cs.main.CoreDistances;
import ca.ualberta.cs.util.Dataset;
import ca.ualberta.cs.util.FairSplitTree;
import ca.ualberta.cs.util.KdTree;
import ca.ualberta.cs.util.PairInt;
import it.unimi.dsi.fastutil.BigList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import ca.ualberta.cs.hdbscanstar.MutualReachabilityDistance;

public class RelativeNeighborhoodGraph {

	public Int2ObjectOpenHashMap<DistanceLevel>[] ExtendedRNG;

	public IntBigArrayBigList[] RNG;

	public int k;

	public static final String WS = "WS";
	public static final String SS = "SS";

	public long numOfEdges = 0;

	public boolean smartFilter;
	public boolean naiveFilter;
	public boolean incremental;

	public boolean index;
	
	public boolean extended;

//	public DistanceCalculator distanceFunction;
	
	public KdTree kdTree;

	
	/**
	 * Relative Neighborhood Graph naive constructor. Takes O(n³) time.
	 * 
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunction
	 * @param k
	 */
	public RelativeNeighborhoodGraph(Dataset dataSet, double[][] coreDistances, DistanceCalculator distanceFunction, int k){

		this.k = k;

		this.extended = false;

		RNG = new IntBigArrayBigList[dataSet.length()];

		for (int i = 0; i < RNG.length; i++) {
			RNG[i] = new IntBigArrayBigList();
		}

		for (int i = 0; i < dataSet.length(); i++) {
			for (int j = i + 1; j < dataSet.length(); j++) {

				if (neighbors(dataSet, i, j, k)) {					

					RNG[i].add(j);
					RNG[j].add(i);

					numOfEdges++;
				}
			}
		}
	}
	

	/**
	 * Relative Neighborhood Graph constructor based on the Well-Separated Pair Decomposition.
	 * 
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunction
	 * @param k
	 * @param s
	 * @param method
	 * @param smartFilter
	 * @param naiveFilter
	 * @param index
	 */
	@SuppressWarnings("unchecked")
	public RelativeNeighborhoodGraph(DistanceCalculator distanceFunction, int k, double s, String method, 
			boolean smartFilter, boolean naiveFilter, boolean incremental, boolean index) {

		this.k = k;

		this.smartFilter = smartFilter;
		this.naiveFilter = naiveFilter;

		this.index = index;
		
		if (this.smartFilter || this.naiveFilter) { 
			this.incremental = incremental;

			this.extended = true;

			ExtendedRNG = new Int2ObjectOpenHashMap[HDBSCANStarRunner.environment.dataset.length()];

			for (int i = 0; i < ExtendedRNG.length; i++) {
				ExtendedRNG[i] = new Int2ObjectOpenHashMap<DistanceLevel>();
			}

		} else {

			this.incremental = false;

			this.extended = false;

			RNG = new IntBigArrayBigList[HDBSCANStarRunner.environment.dataset.length()];

			for (int i = 0; i < RNG.length; i++) {
				RNG[i] = new IntBigArrayBigList();
			}

		}

		if (index) {
			kdTree = new KdTree(HDBSCANStarRunner.environment.dataset);
		}

		// Builds the Fair Split Tree T from dataSet.
		FairSplitTree T = FairSplitTree.build(HDBSCANStarRunner.environment.dataset, HDBSCANStarRunner.environment.coreDistances, this.k, HDBSCANStarRunner.parameters.distanceFunction);
				
		// Finds all the Well-separated Pairs from T.
		findWSPD(T, s, method);

		// "Removes" unnecessary variables.
		T = null;
	}


	/**
	 * Relative Neighborhood Graph constructor based on the Well-Separated Pair Decomposition.
	 * 
	 * @param dataSet
	 * @param coreDistances
	 * @param distanceFunction
	 * @param k
	 * @param smartFilter
	 * @param naiveFilter
	 */
	public RelativeNeighborhoodGraph(DistanceCalculator distanceFunction, int k, 
			boolean smartFilter, boolean naiveFilter, boolean incremental, boolean index) {
		this(distanceFunction, k, 1, RelativeNeighborhoodGraph.WS, smartFilter,  naiveFilter, incremental, index);
	}


	/**
	 * Relative Neighborhood Graph constructor based on the Well-Separated Pair Decomposition.
	 * 
	 * @param dataSet
	 * @param coreDistances
	 * @param k
	 * @param smartFilter
	 * @param naiveFilter
	 */
	public RelativeNeighborhoodGraph(int k, boolean smartFilter, boolean naiveFilter, boolean incremental, boolean index) {
		this(new EuclideanDistance(), k, 1, RelativeNeighborhoodGraph.WS, smartFilter,  naiveFilter, incremental, index);
	}


	/**
	 * @param a
	 * @param b
	 * @param k
	 * @return
	 */
	private boolean neighbors(int a, int b, int k) {

		double w = MutualReachabilityDistance.mutualReachabilityDistance(a, b, k);

		// Smart filter.
		if (smartFilter) {

			double cdA = HDBSCANStarRunner.environment.coreDistances[a][k-1];
			double cdB = HDBSCANStarRunner.environment.coreDistances[b][k-1];

			// Check if the points are in each other's k-neighborhood.
			if (w == Math.max(cdA, cdB)) {

				Integer[] kNN;

				if (cdA > cdB) {
					kNN = CoreDistances.kNN[a];
				} else {
					kNN = CoreDistances.kNN[b];
				}

				for (int i = 0; i < kNN.length; i++) {

					if (HDBSCANStarRunner.environment.coreDistances[kNN[i]][k-1] >= w) continue;

					double dac = MutualReachabilityDistance.mutualReachabilityDistance(a, kNN[i], k);
					double dbc = MutualReachabilityDistance.mutualReachabilityDistance(b, kNN[i], k);

					if (w > Math.max(dac, dbc)) {
						return false;
					}
				}

				return true;
			}

			Integer[] kNNa = CoreDistances.kNN[a];
			Integer[] kNNb = CoreDistances.kNN[b];

			for (int i = 0; i < kNNa.length; i++) {

				if (HDBSCANStarRunner.environment.coreDistances[kNNa[i]][k-1] >= w) continue;

				double dac = MutualReachabilityDistance.mutualReachabilityDistance(a, kNNa[i], k);
				double dbc = MutualReachabilityDistance.mutualReachabilityDistance(b, kNNa[i], k);

				if (w > Math.max(dac, dbc)) {
					return false;
				}
			}		

			for (int i = 0; i < kNNb.length; i++) {

				if (HDBSCANStarRunner.environment.coreDistances[kNNb[i]][k-1] >= w) continue;

				double dac = MutualReachabilityDistance.mutualReachabilityDistance(a, kNNb[i], k);
				double dbc = MutualReachabilityDistance.mutualReachabilityDistance(b, kNNb[i], k);

				if (w > Math.max(dac, dbc)) {
					return false;
				}
			}
		}

		// Index Filter.
		if (index) {
			
			Collection<Integer> lune = kdTree.range(a, HDBSCANStarRunner.parameters.distanceFunction.computeDistance(HDBSCANStarRunner.environment.dataset.row(a), HDBSCANStarRunner.environment.dataset.row(b)));
			
			
			for (Integer c : lune) {
				
				double dac = MutualReachabilityDistance.mutualReachabilityDistance(a, c, k);
				double dbc = MutualReachabilityDistance.mutualReachabilityDistance(b, c, k);

				if (w > Math.max(dac, dbc)) {
					return false;
				}
			}
			
			return true;
		}

		// Naive Filter.
		if (naiveFilter) {			
			if (!neighbors(HDBSCANStarRunner.environment.dataset, a, b, k)) return false;			
		}

		return true;
	}

	/**
	 * @param a
	 * @param b
	 * @param k
	 * @param incremental
	 * @return
	 */
	private int neighbors(int a, int b, int k, boolean incremental) {

		int level = Integer.MAX_VALUE;

		if (neighbors(a, b, k)) {
			level = 0;
		}

		if (incremental) {

			if (level > k) {
				return level;
			}

			int start = 1;
			int end = k - 1;

			while (start <= end) {
				int middle;

				if (start == end) {
					middle = start;
				} else {
					middle = (int) Math.floor((end-start)/2) + start;
				}

				if (neighbors(a, b, middle)) {
					level = middle;
					end = middle - 1;
				} else {
					start = middle + 1;
				}
			}

		}

		return level;
	}

	/**
	 * @param dataSet
	 * @param i
	 * @param j
	 * @param k
	 * @return
	 */
	private boolean neighbors(Dataset dataSet, int i, int j, int k) {
		double dij = MutualReachabilityDistance.mutualReachabilityDistance(i, j, k);

		for (int m = 0; m < dataSet.length(); m++) {

			double dim = MutualReachabilityDistance.mutualReachabilityDistance(i, m, k);
			double djm = MutualReachabilityDistance.mutualReachabilityDistance(j, m, k);

			if (dij > Math.max(dim, djm)) {
				return false;
			}
		}

		return true;
	}
	
	/**
	 * @param T1
	 * @param T2
	 */
	private void SBCN(FairSplitTree T1, FairSplitTree T2) {

		double d;
		double min = Double.MAX_VALUE;
		
		BigList<Integer> tempA = null;
		BigList<Integer> tempB = null;
		
		// Both sets are singletons.
		if (T1.getCount() == 1 && T2.getCount() == 1) {
			addEdge(T1.getP(), T2.getP());
			return;
		}
					
		// One of the sets is a singleton.
		if (T1.getCount() == 1 || T2.getCount() == 1) {
			
			FairSplitTree T = null;

			int p1 = 0;

			if (T1.getCount() == 1) {
				T = T2;
				p1 = T1.getP();
			}
			
			if (T2.getCount() == 1) {
				T = T1;
				p1 = T2.getP();
			}
						
			for (int p2 : T.retrieve()) {

				d = MutualReachabilityDistance.mutualReachabilityDistance(p1, p2, k);

				if (d < min) {
					tempA = new IntBigArrayBigList();
					tempB = new IntBigArrayBigList();
				}

				if (d <= min) {
					min = d;
					tempA.add(p1);
					tempB.add(p2);
				}
			}

			for (int i = 0; i < tempA.size(); i++) {
				addEdge(tempA.get(i), tempB.get(i));
			}

			return;
		}
		
		HashSet<PairInt> AB  = new HashSet<PairInt>();
		IntOpenHashSet B = new IntOpenHashSet((int)T2.getCount());
		
		// General case where both sets have more than one element.
		for (int p1 : T1.retrieve()) {
			for (int p2 : T2.retrieve()) {

				d = MutualReachabilityDistance.mutualReachabilityDistance(p1, p2, k);

				if (d < min) {
					tempA = new IntBigArrayBigList();
					tempB = new IntBigArrayBigList();
				}
				
				if (d <= min) {
					min = d;
					tempA.add(p1);
					tempB.add(p2);
				}
			}

			for (int i = 0; i < tempA.size(); i++) {
				AB.add(new PairInt(tempA.get(i), tempB.get(i)));
				B.add(tempB.get(i));
			}

			min = Double.MAX_VALUE;
		}


		for (int p2 : B) {
			for (int p1 : T1.retrieve()) {
			
				d = MutualReachabilityDistance.mutualReachabilityDistance(p1, p2, k);

				if (d < min) {
					tempA = new IntBigArrayBigList();
					tempB = new IntBigArrayBigList();
				}

				if (d <= min) {
					min = d;
					tempA.add(p1);
					tempB.add(p2);
				}
			}

			for (int i = 0; i < tempA.size(); i++) {

				PairInt candidate = new PairInt(tempA.get(i), tempB.get(i));

				if (AB.contains(candidate)) {
					addEdge(candidate.a, candidate.b);					
				}
			}

			min = Double.MAX_VALUE;
		}

		tempA = null;
		tempB = null;
		AB = null;
	}


	/**
	 * @param vertex a
	 * @param vertex b
	 */
	private void addEdge(int a, int b) {
		int level = neighbors(a, b, k, incremental);

		if (level <= k) {

			if (this.extended) {
				double distance = HDBSCANStarRunner.environment.dataset.computeDistance(a, b);

				DistanceLevel dl = new DistanceLevel(distance, level);

				ExtendedRNG[a].put(b, dl);
				ExtendedRNG[b].put(a, dl);

			} else {
				RNG[a].add(b);
				RNG[b].add(a);							
			}

			numOfEdges++;
		}
	}

	/**
	 * @param T
	 * @param s
	 * @param method
	 */
	private void findWSPD(FairSplitTree T, double s, String method) {
		Stack<Long> stack = new Stack<Long>();

		stack.add(T.id);

		while (!stack.isEmpty()) {
			long i = stack.pop();

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

	/**
	 * @param T1
	 * @param T2
	 * @param s
	 * @param method
	 */
	private void findPairs(FairSplitTree T1, FairSplitTree T2, double s, String method) {

		if (separated(T1, T2, s, method)) {
			SBCN(T1, T2);
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
	 * @param method
	 * @return
	 */
	private static boolean separated(FairSplitTree T1, FairSplitTree T2, double s, String method) {
		
		if (T1.id == T2.id) {
			return false;
		}

		if (method == SS) {
			return ss(T1, T2, s);
		} else {
			return ws(T1, T2, s);
		}		
	}

	/** Method to check whether two FairSplitTrees might emit a RNG edge.
	 * It does not work currently, the core-distance of the points in each subtree would have to be checked.
	 * @param T1
	 * @param T2
	 * @return
	 */
	@SuppressWarnings("unused")
	private static boolean rn(FairSplitTree T1, FairSplitTree T2) {
		double r = FairSplitTree.circleDistance(T1, T2)/2;

		double[] q = new double[T1.center().length];

		for (int i = 0; i < q.length; i++) {
			q[i] = (T1.center()[i] + T2.center()[i])/2;
		}

		BigList<Integer> result = FairSplitTree.rangeSearch(FairSplitTree.root.get(1), q, r, new IntBigArrayBigList());

		if (result.isEmpty()) {
			return true;
		}

		return false;
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
	private static boolean ws(FairSplitTree T1, FairSplitTree T2, double s){

		double d = FairSplitTree.circleDistance(T1, T2);

		if (d >= s*Math.max(T1.diameterMRD(), T2.diameterMRD())) {
			return true;
		} else {
			return false;
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
	private static boolean ss(FairSplitTree T1, FairSplitTree T2, double s){

		double d = FairSplitTree.circleDistance(T1, T2);

		if (d >= s*Math.min(T1.diameterMRD(), T2.diameterMRD())) {
			return true;
		} else {
			return false;
		}
	}

	
	/**
	 * @param i
	 * @param j
	 * @param k
	 * @return
	 */
	public double edgeWeight(int i, int j, int k) {
		if (this.extended) {
//			return Math.max(this.ExtendedRNG[i].get(j).d, Math.max(HDBSCANStarRunner.environment.coreDistances[i][k-1], HDBSCANStarRunner.environment.coreDistances[j][k-1]));
			return MutualReachabilityDistance.mutualReachabilityDistance(i, j, k);
		} else {
			return MutualReachabilityDistance.mutualReachabilityDistance(i, j, k);
		}			
	}

	public static class DistanceLevel {
		public double d;
		public int level;

		/**
		 * @param d
		 * @param level
		 */
		public DistanceLevel(double d, int level) {
			this.d = d;
			this.level = level;
		}
	}
	
	public void toFile(String fileName) {

		try ( BufferedWriter writer = new BufferedWriter(new FileWriter(fileName), 32678)) {
			if (this.extended) {
				for (int i = 0; i < this.ExtendedRNG.length; i++) {
					for (Iterator<Integer> j = this.ExtendedRNG[i].keySet().iterator(); j.hasNext();) {

						int neighbor = j.next();
						
						if (i < neighbor) {
							writer.write(i + " " + neighbor + "\n");
						}
					}
				}			
			} else {
				for (int i = 0; i < this.RNG.length; i++) {
					for (int j = 0; j < this.RNG[i].size64(); j++) {
						if (i < this.RNG[i].getInt(j)) {
							writer.write(i + " " + this.RNG[i].getInt(j) + "\n");
						}
					}
				}
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}