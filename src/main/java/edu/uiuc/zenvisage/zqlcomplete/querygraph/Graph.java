package edu.uiuc.zenvisage.zqlcomplete.querygraph;

import java.util.ArrayList;
import java.util.List;
/**
 * @author Edward Xue
 * Abstraction of a graph
 */
public class Graph {
	
	// Will represent starting points for ALL queries the graph represents
	// (Note each query has its own set of starting points that may not be distinct from other queries)
	protected List<Node> entryNodes;
	
	public Graph() {
		entryNodes = new ArrayList<Node>();
	}

}
