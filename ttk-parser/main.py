from fillingCurve import gilbert2d
import argparse
import csv
import math

class Tree:

    def __init__(self, height, x, y) -> None:
        self.height = height
        self.x = x
        self.y = y
        self.children = []
        self.parent = None
        self.cellId = None

    def __repr__(self) -> str:
        return f'({self.height}, {self.x}, {self.y})'
    
    def getLeaves(self):
        if not self.children:
            return [self]
        leaves = []
        for c in self.children:
            leaves.extend(c.getLeaves())
        return leaves
        

def parseTree(nodePath, edgePath, scale=1):
    with open(nodePath, mode='r', newline='') as f:
        reader = csv.reader(f)
        nodes = {int(row[0]): Tree(float(row[1]), float(row[6]) * scale, float(row[7]) * scale) for i, row in enumerate(reader) if i > 0}

    with open(edgePath, mode='r', newline='') as f:
        reader = csv.reader(f)
        for row in reader:
            if row[1] == "upNodeId":
                continue
            p = nodes[int(row[1])]
            c = nodes[int(row[2])]

            p.children.append(c)
            c.parent = p
    
    root = [node for node in nodes.values() if node.parent is None]
    if not len(root) == 1:
        print("Not a single root detected")
    return root[0]

def boundingBox(tree):
    xMin, xMax = tree.x, tree.x
    yMin, yMax = tree.y, tree.y

    for c in tree.children:
        c_xMin, c_xMax, c_yMin, c_yMax = boundingBox(c)
        xMin = c_xMin if c_xMin < xMin else xMin
        xMax = c_xMax if c_xMax > xMax else xMax
        yMin = c_yMin if c_yMin < yMin else yMin
        yMax = c_yMax if c_yMax > yMax else yMax
    
    return xMin, xMax, yMin, yMax

def boundingBox(root):
    leaves = root.getLeaves()
    l = leaves[0]
    xMin, xMax = l.x, l.x
    yMin, yMax = l.y, l.y
    for l in leaves:
        xMin = l.x if l.x < xMin else xMin
        xMax = l.x if l.x > xMax else xMax
        yMin = l.y if l.y < yMin else yMin
        yMax = l.y if l.y > yMax else yMax

    return xMin, xMax, yMin, yMax


def assignCells(tree):
    # Choose smallest cell id of children as your cell id 
    # note: this can be choosen differently!
    minCellId = math.inf

    if len(tree.children) == 0:
        minCellId = getCell(tree.x, tree.y)
    else:
        for c in tree.children:
            newCellId = assignCells(c)
            minCellId = minCellId if minCellId < newCellId else newCellId
    tree.cellId = minCellId
    return minCellId

def getCell(x, y):
    cell = coords[(math.floor(x), math.floor(y))]
    return cell

def convertTree(tree):
    s = f''
    for child in sorted(tree.children, key=lambda x: x.cellId):
        if child.height < tree.height + 0.005:
            print("Child higher than parent:", child.height, tree.height)
        s += convertTree(child)

    if len(tree.children) > 2:
            print("Wow, more than 2 children!")


    if len(tree.children) == 1:
        return s
    return f'({tree.height}{s})'

def writeToFile(path, tree):
    f = open(path, 'w')
    f.write(tree)
    f.close()

if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument('nodes', type=str)
    parser.add_argument('edges', type=str)
    parser.add_argument('xMin', nargs='?', const=-181, type=float, default=-181)
    parser.add_argument('xMax', nargs='?', const=181, type=float, default=181)
    parser.add_argument('yMin', nargs='?', const=-87, type=float, default=-87)
    parser.add_argument('yMax', nargs='?', const=87, type=float, default=87)
    parser.add_argument('scale', nargs='?', const=1, type=float, default=1)
    args = parser.parse_args()

    # Parse tree
    root = parseTree(args.nodes, args.edges, scale=args.scale)  

    # Compute cells of space filling curve
    cells = gilbert2d(math.ceil(args.xMax) - math.floor(args.xMin), math.ceil(args.yMax) - math.floor(args.yMin))
    cells = [(x + math.floor(args.xMin), y + math.floor(args.yMin)) for x, y in cells]
    coords = {cell : i for i, cell in enumerate(cells)}
    
    # Compute cells of each leaf
    assignCells(root)

    # Output ordered merge tree
    tree = convertTree(root)
    print(tree)