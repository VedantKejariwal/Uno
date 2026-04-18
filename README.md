# Uno AI — Monte Carlo Tree Search

Two AI agents that play Uno using Monte Carlo Tree Search, implemented from scratch in Java for CS440 (Artificial Intelligence) at Boston University.

## What it does

The agents play Uno by simulating thousands of random games from each possible move and picking the one that wins most often. No hardcoded rules. The agents figure out strategy purely from outcome statistics.

## Agents

**ExpectedOutcomeAgent** runs flat Monte Carlo evaluation. It creates one child node per legal move at the root, hammers each with random rollouts, and picks the highest average outcome. Simple and effective.

**UCTAgent** builds the search tree dynamically using the UCB1 formula to balance exploration and exploitation at every level. After each rollout, Q-values propagate back through every node on the path to root, making the tree smarter with each iteration.

## Algorithms and concepts

Monte Carlo Tree Search, Upper Confidence Trees (UCT), UCB1 explore/exploit formula, backpropagation, Q-value estimation, rollout simulation, heuristic reward shaping, anytime search.

## How to run

```bash
javac -cp "./lib/*" -d . src/pas/uno/agents/*.java

java -cp "./lib/*:." edu.bu.pas.uno.SingleGameMain pas.uno.agents.UCTAgent edu.bu.pas.uno.agents.RandomAgent
```

For a 4-player game with a fixed seed:

```bash
java -cp "./lib/*:." edu.bu.pas.uno.SingleGameMain --seed 42 --maxThinkingTimeInMS 2000 pas.uno.agents.UCTAgent edu.bu.pas.uno.agents.RandomAgent edu.bu.pas.uno.agents.RandomAgent edu.bu.pas.uno.agents.RandomAgent
```

## Tech

Java, Java Swing (frontend), Monte Carlo Tree Search, UCB1, game tree search.
