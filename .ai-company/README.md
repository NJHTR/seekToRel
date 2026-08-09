# AI Software Company Operating System

This directory is the operating system for the autonomous product organization
working in this repository. It defines who may make a decision, what evidence
is required before code moves forward, and how multiple products share platform
capabilities without sharing accidental implementation details.

## Current portfolio

| Product | User-facing role | Current implementation |
| --- | --- | --- |
| Mac for Web | macOS-style desktop shell and portfolio launcher | `nexus-intelligence-workspace` |
| Nexus | Experiment-report automation Agent application | ReportAI Spring modules plus the Nexus workspace |
| Portfolio apps | Demonstration applications embedded in the shell | Store, Reddit, Weather and the portfolio project |

`ReportAI` remains the Java repository and backend technical identifier. It is
not a second user-facing product.

## How to use this directory

- `agents/` contains role contracts. A role may act only within its authority.
- `governance/` contains mandatory engineering and release gates.
- `projects/` contains one status surface per product and a reusable project template.
- `docs/` contains the company roadmap, task board, decisions and changelog.
- `knowledge/`, `decisions/`, `reports/` and `memory/` hold durable context; none
  of them is a substitute for source-of-truth code or test evidence.

## Non-negotiable loop

`discover -> propose -> plan -> architect -> design -> implement -> review -> test -> secure -> release -> learn`

An agent must leave an evidence link and update the relevant project status at
the end of every work item. A green compilation alone never counts as release
approval.

## First operating baseline

The organization is being bootstrapped before the next product-code iteration.
The existing ReportAI/Nexus code is treated as a working baseline with known
release blockers documented in `.ai/PROJECT_STATUS.md` and
`docs/production-release-checklist.md`. Bootstrap documents must not claim those
blockers are solved.
