# Company Task Board

Legend: `TODO`, `READY`, `IN PROGRESS`, `REVIEW`, `BLOCKED`, `DONE`.

| ID | Product | Work item | Owner group | State | Evidence/blocked by |
| --- | --- | --- | --- | --- | --- |
| ORG-001 | Company | Create role contracts and governance | Executive/PM | DONE | Bootstrap Prompt and structural audit |
| ORG-002 | Company | Cross-review organization docs and assign named owners | CTO/QA/Security | DONE | [ORG-002 cross-functional review](../reports/ORG-002-CROSS-FUNCTIONAL-REVIEW.md); product release gates remain active |
| NEXUS-101 | Nexus | Durable task-event history and query API | Backend/Platform | REVIEW | PostgreSQL sequence/FK/cursor implementation complete; Docker restart and tenant evidence pending |
| NEXUS-102 | Nexus | Agent Observatory task timeline and recovery UI | Frontend/UX | REVIEW | Archive/reducer/reconnect implementation complete; browser refresh/reconnect evidence pending |
| NEXUS-103 | Nexus | Credentialed DOCX upload-to-export acceptance | QA/Runtime/LLM | BLOCKED | Rotated provider secret and staging evidence |
| NEXUS-104 | Nexus | Email verification and password recovery | Auth/Security | TODO | Product policy decision |
| SHELL-101 | Mac for Web | App manifest, version and health contract | Desktop/Platform | TODO | Architecture review |
| SHELL-102 | Mac for Web | Add Notes/knowledge and developer-studio app briefs | Product/UX | TODO | Product discovery |
| OPS-101 | Platform | CI CVE, backup/restore and canary evidence | Quality/Security/Operations | BLOCKED | CI/staging environment |

Every row must link to a project brief or code change before it is moved to
`DONE`. A blocked row records the external dependency and an owner for the next
attempt; it is not silently removed from the board.
