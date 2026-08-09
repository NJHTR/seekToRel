import React, { useState } from 'react';
import { DocumentItem, AgentItem, NavigationTab } from '../types';

interface ForgeViewProps {
  documents: DocumentItem[];
  agents: AgentItem[];
  onSelectDocument: (docId: string) => void;
  onNewProject: () => void;
  onNavigateTab: (tab: NavigationTab) => void;
}

export const ForgeView: React.FC<ForgeViewProps> = ({
  documents,
  agents,
  onSelectDocument,
  onNewProject,
  onNavigateTab
}) => {
  const [searchQuery, setSearchQuery] = useState('');

  const filteredDocs = documents.filter((doc) =>
    doc.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    doc.summary.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="flex-1 px-8 py-12 max-w-[1200px] mx-auto w-full animate-fade-in">
      {/* Hero Section */}
      <section className="mb-16">
        <h1 className="font-display-lg text-display-lg text-[#1a1c1c] mb-8 max-w-3xl">
          Welcome to your creative forge.
        </h1>
        <div className="flex items-center gap-4 max-w-2xl">
          <div className="relative flex-1">
            <span className="material-symbols-outlined absolute left-0 top-1/2 -translate-y-1/2 text-[#777587] pb-2 pl-2">
              search
            </span>
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search knowledge base, drafts, or agents..."
              className="search-input w-full bg-transparent border-0 border-b border-[#c7c4d8] pb-2 pl-10 text-body-md font-body-md text-[#1a1c1c] placeholder-[#777587] focus:ring-0 focus:border-b-[#3525cd] outline-none transition-colors"
            />
          </div>
          <button
            onClick={onNewProject}
            className="bg-[#3525cd] text-white px-6 py-2.5 rounded font-body-md text-body-md hover:bg-[#4f46e5] transition-colors shrink-0 shadow-xs flex items-center gap-1.5 cursor-pointer active:scale-98"
          >
            <span className="material-symbols-outlined text-[20px]">add</span>
            New Project
          </button>
        </div>
      </section>

      {/* Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Recent Documents Section (8 Columns) */}
        <section className="lg:col-span-8 flex flex-col gap-6">
          <div className="flex items-center justify-between border-b border-[#c7c4d8] pb-2">
            <h2 className="font-headline-sm text-headline-sm text-[#1a1c1c]">
              Recent Documents
            </h2>
            <button
              onClick={() => onNavigateTab('library')}
              className="font-label-caps text-label-caps text-[#464555] hover:text-[#3525cd] transition-colors flex items-center gap-1 cursor-pointer"
            >
              VIEW ALL <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {filteredDocs.map((doc) => (
              <div
                key={doc.id}
                onClick={() => onSelectDocument(doc.id)}
                className="document-card bg-white border border-[#c7c4d8] rounded p-6 flex flex-col gap-4 cursor-pointer hover:border-[#c3c0ff] group shadow-2xs"
              >
                <div className="flex justify-between items-start">
                  <span
                    className={`font-label-caps text-label-caps px-2 py-0.5 rounded ${
                      doc.status === 'DRAFT'
                        ? 'bg-[#e8e8e7] text-[#464555]'
                        : 'bg-emerald-100 text-emerald-800'
                    }`}
                  >
                    {doc.status}
                  </span>
                  <span className="material-symbols-outlined text-[#c7c4d8] group-hover:text-[#464555]">
                    more_horiz
                  </span>
                </div>

                <div>
                  <h3 className="font-headline-sm text-headline-sm text-[#1a1c1c] mb-2 group-hover:text-[#3525cd] transition-colors">
                    {doc.title}
                  </h3>
                  <p className="font-body-md text-body-md text-[#464555] line-clamp-2">
                    {doc.summary}
                  </p>
                </div>

                <div className="mt-auto pt-4 flex items-center justify-between text-[#777587] text-sm">
                  <span className="font-label-caps text-label-caps">{doc.lastEdited}</span>
                  <div className="flex -space-x-2">
                    {doc.avatars.map((avatar, idx) => (
                      <div
                        key={idx}
                        className="w-6 h-6 rounded-full border border-white overflow-hidden bg-[#e8e8e7]"
                      >
                        <img src={avatar} alt="Avatar" className="w-full h-full object-cover" />
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* Active Agents Section (4 Columns) */}
        <section className="lg:col-span-4 flex flex-col gap-6">
          <div className="flex items-center justify-between border-b border-[#c7c4d8] pb-2">
            <h2 className="font-headline-sm text-headline-sm text-[#1a1c1c]">
              Active Agents
            </h2>
            <span className="bg-[#3525cd]/10 text-[#3525cd] font-label-caps text-label-caps px-2.5 py-1 rounded-full flex items-center gap-1.5 font-semibold">
              <span className="w-2 h-2 rounded-full bg-[#3525cd] animate-pulse"></span>
              {agents.length} RUNNING
            </span>
          </div>

          <div className="flex flex-col gap-4">
            {agents.map((agent) => (
              <div
                key={agent.id}
                onClick={() => onNavigateTab('flow')}
                className="bg-[#f0f0ff] border border-[#c3c0ff] border-l-4 agent-pulse rounded p-4 cursor-pointer hover:shadow-xs transition-shadow"
              >
                <div className="flex items-center gap-3 mb-2">
                  <span className="material-symbols-outlined text-[#3525cd]">
                    {agent.icon}
                  </span>
                  <span className="font-label-caps text-label-caps text-[#3525cd] tracking-widest uppercase font-bold">
                    {agent.name}
                  </span>
                </div>
                <p className="font-body-md text-body-md text-[#464555] text-sm mb-3">
                  {agent.description}
                </p>
                <div className="flex items-center justify-between mt-2 pt-2 border-t border-[#c3c0ff]/50">
                  <span className="font-label-caps text-label-caps text-[#3525cd]/80">
                    {agent.statusText}
                  </span>
                  {agent.progress !== undefined ? (
                    <div className="w-16 h-1 bg-[#e8e8e7] rounded-full overflow-hidden">
                      <div
                        className="h-full bg-[#3525cd]"
                        style={{ width: `${(agent.progress / (agent.total || 100)) * 100}%` }}
                      ></div>
                    </div>
                  ) : agent.isSpinning ? (
                    <span className="material-symbols-outlined text-[#3525cd] text-sm animate-spin">
                      sync
                    </span>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};
