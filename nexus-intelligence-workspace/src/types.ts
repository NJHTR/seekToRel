export type NavigationTab = 'forge' | 'library' | 'flow' | 'editor' | 'archive';

export interface DocumentItem {
  id: string;
  title: string;
  status: 'DRAFT' | 'PUBLISHED';
  summary: string;
  category: string;
  lastEdited: string;
  avatars: string[];
  content?: {
    typeReport: string;
    headline: string;
    summaryText: string;
    suggestion?: {
      id: string;
      text: string;
      docRef: string;
    };
    bodyParagraphs: string[];
    growthVectors: { id: string; title: string; completed: boolean }[];
  };
}

export interface AgentItem {
  id: string;
  name: string;
  icon: string;
  description: string;
  statusText: string;
  progress?: number;
  total?: number;
  isSpinning?: boolean;
}

export interface TaskBlock {
  id: string;
  name: string;
  status: 'completed' | 'in_progress' | 'waiting' | 'pending';
  progress?: number;
  confidence?: string;
  subtitle?: string;
  timePosition: string; // e.g. "left-4 w-[25%]"
}

export interface PipelineRow {
  id: string;
  agentName: string;
  agentId: string;
  icon: string;
  tags: string[];
  status: 'Active' | 'Waiting' | 'External';
  tasks: TaskBlock[];
}

export interface KnowledgeDocument {
  id: string;
  name: string;
  collection: string;
  dateAdded: string;
  status: 'ANALYZING' | 'INDEXED' | 'PARSING FAILED';
}

export interface ReasoningLog {
  id: string;
  timeAgo: string;
  type: 'Thought' | 'Action' | 'Result';
  title: string;
  details: string;
  active?: boolean;
}
