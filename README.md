# Visualizing monotone interleavings
This repository implements ParkView, a method for visualizing monotone interleavings.
![image](https://github.com/user-attachments/assets/a3b2e169-3a10-4241-87f3-605546f360c0)

To run ParkView, do the following.
1. Download the repository.
2. Open the repository directory in the terminal.
3. Execute `.\gradlew run` for Windows or `./gradlew run` for a Unix-based operating system (make sure Java is installed properly; we use [OpenJDK](https://openjdk.org/) version 20). This should open a window with ParkView.
4. A pair of merge trees is loaded by default. To visualize custom merge trees, drag and drop a pair of `.txt` files representing merge trees into the application. 
  
Key F11 opens and closes a GUI panel, which can be used to export the trees to SVG.

## Merge tree format
We represent ordered merge trees using a plain text format.
Specifically, an ordered merge tree is represented by a pair of parenthesis that has within it first the height of the root of the tree, and then a sequence of subtrees.
For example, consider a tree rooted at height 10, with two subtrees: 1. a leaf at height 5, and 2. an internal node at height 3 with two leaves at heights 2 and 0.
This is encoded as follows:
```
(10(5)(3(2)(0)))
```
We use [The Topology Toolkit](https://topology-tool-kit.github.io/) to generate merge trees whose arcs and nodes we export to CSV files.
We transform the CSV files into ordered merge trees in our format using the Python script [`ttk-parser/main.py`](ttk-parser/main.py).
The script expects the following arguments: 
```
main.py {nodes} {arcs} {minX} {maxX} {minY} {maxY} {scale}
```
Here `{arcs}` and `{nodes}` are the paths to the CSV files containing the corresponding parts of the merge tree output by TTK.
The remaining arguments are needed to [create the space filling curve](https://github.com/jakubcerveny/gilbert/) used for determining an order on the leaves.
The four arguments `{minX}`, `{maxX}`, `{minY}`, and `{maxY}` specify the bounding box of the scaled scalar field using scaling factor `{scale}`.
Scaling may be necessary for some scalar fields as the cells of the space filling curve are each a unit square.

Directory [`data/merge-trees`](data/merge-trees) contains the ordered merge trees in txt format used in the paper.
