# Visualizing monotone interleavings
This repository implements ParkView, a method for visualizing monotone interleavings.
![image](https://github.com/user-attachments/assets/a3b2e169-3a10-4241-87f3-605546f360c0)


To run the code, use the Gradle wrapper `gradlew` as follows: `./gradlew run`. The code has been tested using [OpenJDK](https://openjdk.org/) version 20. We recommend using an IDE such as [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) to run the code. One can drag and drop `.txt` files of merge trees into the application to load them. Press F11 to toggle the GUI panel.

## Merge tree format
We represent ordered merge trees using a plain text format.
Specifically, an ordered merge tree is represented by a pair of parenthesis that has within it first the height of the root of the tree, and then a sequence of subtrees.
For example, consider a tree rooted at height 10, with two subtrees: 1. a leaf at height 5, and 2. an internal node at height 3 with two leaves at heights 2 and 0.
This is encoded as follows:
```
(10(5)(3(2)(0)))
```
