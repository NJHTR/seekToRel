import { DocumentItem, AgentItem, PipelineRow, KnowledgeDocument, ReasoningLog } from './types';

export const USER_AVATAR = "https://lh3.googleusercontent.com/aida-public/AB6AXuAx6repBjYZIMeE8wiiacM1ReIsRJRgiwzvS03KDpgCCqsm6CjFUUqXDXZjITc3WeiUji1AO-ldBd7xUSd3iaGnbZJYAJI_KRh862kXT2Vwa9bRlMWwDdhDUwOfh9A4mojww6YkMn2o1WOaYT5aujpbYKL03XkiP78HsLzN0ByLkOGyxbF7KqjIlzRwers7BwF3zDzbL-CA-F1C9l2NbDDKRqS9wQxM7VV1V0RSJBzMuteGpxXrbYdg7w";

export const COLLABORATOR_1 = "https://lh3.googleusercontent.com/aida-public/AB6AXuCkAQlEVZ3xCo759YjECxUyCCG2__2ycNa8GPblJZqdcUEdQphogdV1apSTuwIjhHudNGpvTFo2oJOl_n7ZrDxomloMRG77KE7jvKZ0n8uOSPAA2O9OW8uBQ14TJdt5a7xVOAqrru58MCPhZVHWy7IB9TUQhzlQG1wmV9hNWAcU3sWJk2mLnPCcmMXFXMSPDLubtyJGq86_Ph33xC3odggiZ9qNLuEVjlMA0k43Jb88G3bSVG3DnhH3aQ";

export const COLLABORATOR_2 = "https://lh3.googleusercontent.com/aida-public/AB6AXuCShh8WkmWyTmth3GTu3ei9NzC9babIjadtRVCGs3Pcu0B4MQSYFqxBXFqVj2BhgLbcLvxs_BMqFtF3vQsuHWwd90maL4s82RjTQgWyvJQLIxlKSzTKLHRvz11VQcqGtv7KF6rzrBdebdmX16oj2dGdptLu80tUnb_I2JkpRvmOuyBl36egLfAkP7N6liLOC7Qxw_f21KcFgWzbvbc6ehevihgdBwnXZfVHUnA4nkYilxl0sJXWbGRR9A";

export const INITIAL_DOCUMENTS: DocumentItem[] = [
  {
    id: "doc-1",
    title: "Q3 Strategy Synthesis",
    status: "DRAFT",
    summary: "Aggregating market trends and internal performance metrics to define the upcoming quarterly OKRs. Focus on expansion in APAC region.",
    category: "Market Research",
    lastEdited: "Edited 2h ago",
    avatars: [COLLABORATOR_1, COLLABORATOR_2]
  },
  {
    id: "doc-2",
    title: "Brand Identity v2",
    status: "PUBLISHED",
    summary: "Updated typographic guidelines and color palettes for the upcoming rebranding initiative. Includes component library specs.",
    category: "Design",
    lastEdited: "Edited yesterday",
    avatars: [USER_AVATAR]
  },
  {
    id: "doc-3",
    title: "Strategic Market Analysis 2024",
    status: "DRAFT",
    summary: "Comprehensive market dynamics evaluation and competitive landscape assessment for fiscal year 2024.",
    category: "Market Research",
    lastEdited: "Edited 10m ago",
    avatars: [COLLABORATOR_1, COLLABORATOR_2],
    content: {
      typeReport: "Q3 STRATEGY REPORT",
      headline: "Executive Summary: Market Dynamics",
      summaryText: "The current fiscal quarter has demonstrated a significant shift in enterprise software adoption, primarily driven by the accelerated integration of generative AI models into core operational workflows. Initial analysis suggests a 45% reduction in time-to-market for early adopters.",
      suggestion: {
        id: "sug-1",
        text: "Consider citing the recent internal audit (Doc-Ref: Q2-Performance.pdf) which explicitly details the infrastructure costs associated with this adoption rate. It strengthens the fiscal argument.",
        docRef: "Doc-Ref: Q2-Performance.pdf"
      },
      bodyParagraphs: [
        "Furthermore, competitive analysis indicates that incumbent market leaders are rapidly depreciating legacy technical debt to reallocate capital towards predictive analytics suites. This creates a critical window of opportunity for disruptive entrants targeting niche vertical integrations."
      ],
      growthVectors: [
        { id: "gv-1", title: "Automated Compliance Monitoring (Projected 22% YoY)", completed: true },
        { id: "gv-2", title: "Synthetic Data Generation for ML Training", completed: true },
        { id: "gv-3", title: "Edge Computing Infrastructure (Awaiting Data)", completed: false }
      ]
    }
  }
];

export const INITIAL_AGENTS: AgentItem[] = [
  {
    id: "agent-1",
    name: "DATA MINER ALPHA",
    icon: "psychology",
    description: "Scraping competitor pricing models and feature matrices from specified URLs.",
    statusText: "Processing 45/100 sources...",
    progress: 45,
    total: 100
  },
  {
    id: "agent-2",
    name: "DRAFTSMITH",
    icon: "edit_document",
    description: "Synthesizing raw meeting transcripts into structured executive summaries.",
    statusText: "Structuring output...",
    isSpinning: true
  }
];

export const INITIAL_PIPELINES: PipelineRow[] = [
  {
    id: "pipe-1",
    agentName: "Research Agent",
    agentId: "ID: RX-992",
    icon: "travel_explore",
    tags: ["Data Mining"],
    status: "Active",
    tasks: [
      {
        id: "task-1",
        name: "Initial Scrape",
        status: "completed",
        timePosition: "left-4 w-[25%]"
      },
      {
        id: "task-2",
        name: "Semantic Analysis",
        status: "in_progress",
        confidence: "Conf: 94%",
        subtitle: "Processing latest quarterly reports...",
        progress: 70,
        timePosition: "left-[calc(35%+16px)] w-[30%]"
      }
    ]
  },
  {
    id: "pipe-2",
    agentName: "Synthesis Agent",
    agentId: "ID: SY-401",
    icon: "hub",
    tags: ["Structuring"],
    status: "Waiting",
    tasks: [
      {
        id: "task-3",
        name: "Draft Assembly",
        status: "waiting",
        subtitle: "Awaiting Research Agent",
        timePosition: "left-[calc(65%+16px)] w-[20%]"
      }
    ]
  },
  {
    id: "pipe-3",
    agentName: "Review Agent",
    agentId: "ID: RV-008",
    icon: "fact_check",
    tags: ["Validation"],
    status: "Waiting",
    tasks: [
      {
        id: "task-4",
        name: "Compliance Check",
        status: "pending",
        timePosition: "left-[calc(85%+32px)] w-[10%]"
      }
    ]
  },
  {
    id: "pipe-4",
    agentName: "Market Data API",
    agentId: "External",
    icon: "api",
    tags: ["API"],
    status: "External",
    tasks: [
      {
        id: "task-5",
        name: "Fetching Indexes",
        status: "in_progress",
        timePosition: "left-[calc(30%+16px)] w-[15%]"
      }
    ]
  }
];

export const INITIAL_KNOWLEDGE_DOCS: KnowledgeDocument[] = [
  {
    id: "k-1",
    name: "Q3_Market_Analysis_Global.pdf",
    collection: "Market Research",
    dateAdded: "Today, 09:41 AM",
    status: "ANALYZING"
  },
  {
    id: "k-2",
    name: "Competitor_Pricing_Strategy_2023.csv",
    collection: "Market Research",
    dateAdded: "Yesterday",
    status: "INDEXED"
  },
  {
    id: "k-3",
    name: "Confidential_Settlement_Agreement_v1.docx",
    collection: "Legal Precedents",
    dateAdded: "Oct 12, 2023",
    status: "PARSING FAILED"
  },
  {
    id: "k-4",
    name: "API_Documentation_v2.md",
    collection: "Technical Specs",
    dateAdded: "Oct 10, 2023",
    status: "INDEXED"
  }
];

export const INITIAL_REASONING_LOGS: ReasoningLog[] = [
  {
    id: "log-1",
    timeAgo: "Just now",
    type: "Thought",
    title: "Context Window Analysis",
    details: "Analyzing context window. High density of supply chain metrics detected in Doc-A4.",
    active: true
  },
  {
    id: "log-2",
    timeAgo: "2m ago",
    type: "Action",
    title: "Action: Search",
    details: "Query: \"LogiCorp Q3 forward looking statements\"",
    active: false
  },
  {
    id: "log-3",
    timeAgo: "5m ago",
    type: "Result",
    title: "Entity Extraction",
    details: "Successfully extracted 4 competitor pricing tiers and 12 infrastructure cost vectors.",
    active: false
  }
];
