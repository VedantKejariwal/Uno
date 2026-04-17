# Technical Product Requirement Document (PRD)
**Target Agent:** Cursor (GPT-5.4)
**Task:** Implement Depth-Thresholded Minimax for Recursive Tic-Tac-Toe
**Target File:** `src/labs/rttt/agents/DepthThresholdedMinimaxAgent.java`

## 1. Context & Objective
We are building an adversarial search agent for Recursive Tic-Tac-Toe. Vanilla minimax takes hours to complete a single move due to the massive state space ($O(9^{81})$). The goal is to implement **Depth-Thresholded Minimax**. 

You will complete the `minimax(Node node)` method in `DepthThresholdedMinimaxAgent.java`.

## 2. Strict Constraints (CRITICAL)
* **Minimalist & Human-Like:** Write code exactly as a student would following a lecture. Keep it simple and direct.
* **No Comments:** Do NOT add any inline comments explaining the logic. Do not add JavaDocs. Leave the existing starter comments exactly as they are.
* **Adhere to Lecture Logic:** You MUST strictly implement the pseudo-code provided in the course lectures (detailed in Section 4). Do not invent custom optimizations, alpha-beta pruning (yet), or early exits not specified in the algorithm.

## 3. Core Logic Requirements
1.  **Terminal Check:** If `node.isTerminal()` is true, the node already has its utility set (+100, -100, or 0). Return the `node`.
2.  **Depth Threshold Check:** If `node.getDepth() >= this.getMaxDepth()`, the depth budget is exhausted. 
    * Calculate the heuristic utility using `Heuristics.calculateHeuristicValue(node)`.
    * Set this utility on the node using `node.setUtility(...)`.
    * Return the `node`.
3.  **Recursive Step (Minimax):**
    * Determine if the current node is a maximizing node (usually `node.isMaxNode()` or checking if `node.getCurrentPlayerType()` is the agent's player type).
    * Initialize tracking variables: `bestNode` (null) and `bestUtility` (Negative Infinity for Max, Positive Infinity for Min).
    * Iterate over `node.getChildren()`.
    * For each `child`:
        * Recursively call `minimax(child)`.
        * Extract the utility from the returned node.
        * Set this utility on the current `child` using `child.setUtility(...)`.
        * Compare this utility against `bestUtility`. If it is better (strictly greater for Max, strictly less for Min), update `bestUtility` and set `bestNode = child`.
    * Return `bestNode`.

## 4. Required Classes, Methods, and Data Types (JavaDocs Context)

| Class / Enum | Package | Relevant Methods / Roles |
| :--- | :--- | :--- |
| `Node` | `edu.bu.labs.rttt.traversal` | `isTerminal()`, `getDepth()`, `getChildren()`, `getUtility()`, `setUtility(double)`, `isMaxNode()`, `getCurrentPlayerType()` |
| `Heuristics` | `src.labs.rttt.heuristics` | `calculateHeuristicValue(Node node)` (Returns `double`) |
| `PlayerType` | `edu.bu.labs.rttt.game` | Enums: `X`, `O`. Represents the maximizing/minimizing player. |
| `DepthThresholdedMinimaxAgent`| `src.labs.rttt.agents` | Contains `getMaxDepth()`, extends `SearchAgent`. |
| `Coordinate` | `edu.bu.labs.rttt.utils` | Represents the move embedded inside a `Node`. |

## 5. Lecture Notes Context (Algorithm Foundation)
*Do not implement Alpha-Beta pruning yet. Stick to the foundational Depth-Thresholded Minimax described below.*

* **Adversarial Search I (The Core Idea):** Games are modeled as trees. Players have opposite goals (maximizing vs. minimizing utility). Terminal states define absolute wins (+100) or losses (-100).
* **Adversarial Search II (Depth-Thresholded Minimax):** Expanding the whole tree is practically impossible. We stop the DFS expansion early based on a depth limit. When stopped at a non-terminal leaf, we guess the utility using a heuristic function. The parent nodes choose the child that leads to the best terminal or heuristic grandchild.
* **Adversarial Search III (Future Context):** Outlines advanced techniques like Alpha-Beta Pruning (ignoring branches that mathematically cannot influence the outcome) and Move Ordering (sorting children to maximize pruning efficiency). 

**Action:** Complete `minimax(Node node)` in `DepthThresholdedMinimaxAgent.java` using exactly these rules.