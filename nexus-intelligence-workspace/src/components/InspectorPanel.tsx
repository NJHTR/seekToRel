import React, { useState } from 'react';
import { ReasoningLog } from '../types';

interface InspectorPanelProps {
  logs: ReasoningLog[];
  onTriggerAgent?: (action: string) => void;
  isOpen: boolean;
  onClose?: () => void;
}

export const InspectorPanel: React.FC<InspectorPanelProps> = ({
  logs,
  onTriggerAgent,
  isOpen
}) => {
  const [activeTab, setActiveTab] = useState<'reasoning' | 'tools' | 'sources'>('reasoning');

  if (!isOpen) return null;

  return (
    <aside className="bg-[#f3f4f3] font-label-caps text-label-caps docked h-screen w-80 right-0 top-0 border-l border-[#c7c4d8] fixed flex flex-col z-30 pt-16 transition-all duration-300 shadow-md">
      {/* Header */}
      <div className="p-5 border-b border-[#c7c4d8] flex justify-between items-center bg-[#f9f9f8]">
        <div>
          <h2 className="font-label-caps text-label-caps uppercase tracking-widest text-[#4b4746]">
            The Inspector
          </h2>
          <p className="text-[10px] text-[#464555]">Agent Intelligence</p>
        </div>
        <button className="text-[#464555] hover:text-[#3525cd] transition-colors">
          <span className="material-symbols-outlined text-[20px]">more_horiz</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-[#c7c4d8] bg-[#ffffff]">
        <button
          onClick={() => setActiveTab('reasoning')}
          className={`flex-1 py-3 flex justify-center items-center gap-1.5 transition-colors border-b-2 ${
            activeTab === 'reasoning'
              ? 'text-[#3525cd] border-[#3525cd] font-semibold'
              : 'text-[#464555] border-transparent hover:bg-[#f3f4f3]'
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">psychology</span> Reasoning
        </button>
        <button
          onClick={() => setActiveTab('tools')}
          className={`flex-1 py-3 flex justify-center items-center gap-1.5 transition-colors border-b-2 ${
            activeTab === 'tools'
              ? 'text-[#3525cd] border-[#3525cd] font-semibold'
              : 'text-[#464555] border-transparent hover:bg-[#f3f4f3]'
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">build</span> Tools
        </button>
        <button
          onClick={() => setActiveTab('sources')}
          className={`flex-1 py-3 flex justify-center items-center gap-1.5 transition-colors border-b-2 ${
            activeTab === 'sources'
              ? 'text-[#3525cd] border-[#3525cd] font-semibold'
              : 'text-[#464555] border-transparent hover:bg-[#f3f4f3]'
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">format_quote</span> Sources
        </button>
      </div>

      {/* Content Body */}
      <div className="p-4 flex-grow overflow-y-auto space-y-6">
        {activeTab === 'reasoning' && (
          <>
            {/* Active Process Card */}
            <div className="bg-[#3525cd]/5 border-l-4 border-[#3525cd] rounded-r-lg p-4 relative shadow-2xs">
              <div className="flex items-center gap-2 mb-2">
                <span className="material-symbols-outlined text-[#3525cd] text-sm animate-spin">
                  sync
                </span>
                <span className="font-label-caps text-[11px] text-[#3525cd] uppercase tracking-wider font-semibold">
                  Active Process
                </span>
              </div>
              <h4 className="font-body-md text-sm font-semibold text-[#1a1c1c] mb-2">
                Analyzing 12 source documents...
              </h4>
              <div className="w-full bg-[#e2e2e2] rounded-full h-1.5 mb-3 overflow-hidden">
                <div className="bg-[#3525cd] h-1.5 rounded-full w-[65%] transition-all duration-500"></div>
              </div>
              <div className="space-y-1.5 text-[#464555] text-[11px]">
                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[14px] text-[#3525cd]">check</span>
                  <span>Cross-referencing Q1/Q2 internal reports</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[14px] text-[#3525cd]">check</span>
                  <span>Synthesizing competitor API documentation</span>
                </div>
                <div className="flex items-center gap-1.5 text-[#777587]">
                  <span className="material-symbols-outlined text-[14px] animate-pulse">more_horiz</span>
                  <span className="italic">Extracting fiscal projections...</span>
                </div>
              </div>
            </div>

            {/* Reasoning Log Stream */}
            <div className="space-y-4">
              <h3 className="font-label-caps text-[11px] text-[#777587] uppercase tracking-widest">
                Reasoning Log
              </h3>
              {logs.map((log) => (
                <div key={log.id} className="p-3 rounded bg-white border border-[#c7c4d8] shadow-2xs relative">
                  <div className="flex justify-between items-center mb-1">
                    <span className={`text-[10px] font-bold uppercase tracking-wider ${
                      log.type === 'Thought' ? 'text-[#3525cd]' : log.type === 'Action' ? 'text-[#645d58]' : 'text-emerald-700'
                    }`}>
                      {log.type}
                    </span>
                    <span className="text-[9px] text-[#777587]">{log.timeAgo}</span>
                  </div>
                  <p className="font-body-md text-[12px] text-[#464555] leading-tight">
                    {log.details}
                  </p>
                </div>
              ))}
            </div>

            {/* Extracted Key Themes */}
            <div className="space-y-3">
              <h3 className="font-label-caps text-[11px] text-[#777587] uppercase tracking-widest">
                Extracted Key Themes
              </h3>
              <div className="bg-white rounded-md p-3 border border-[#c7c4d8] hover:border-[#3525cd]/50 transition-colors cursor-pointer">
                <div className="flex justify-between items-center mb-1">
                  <span className="font-body-md text-xs font-semibold text-[#1a1c1c]">Generative Integration</span>
                  <span className="font-label-caps text-[10px] bg-[#3525cd]/10 text-[#3525cd] px-1.5 py-0.5 rounded">High Confidence</span>
                </div>
                <p className="font-body-md text-[11px] text-[#464555]">
                  Consistent pattern of reduced time-to-market across 4 cited case studies.
                </p>
              </div>
              <div className="bg-white rounded-md p-3 border border-[#c7c4d8] hover:border-[#3525cd]/50 transition-colors cursor-pointer">
                <div className="flex justify-between items-center mb-1">
                  <span className="font-body-md text-xs font-semibold text-[#1a1c1c]">Legacy Depreciation</span>
                  <span className="font-label-caps text-[10px] bg-[#e2e2e2] text-[#464555] px-1.5 py-0.5 rounded">Medium Confidence</span>
                </div>
                <p className="font-body-md text-[11px] text-[#464555]">
                  Incumbents reallocating budget from maintenance to predictive models.
                </p>
              </div>
            </div>
          </>
        )}

        {activeTab === 'tools' && (
          <div className="space-y-4">
            <h3 className="font-label-caps text-[11px] text-[#777587] uppercase tracking-widest">
              Available Agent Actions
            </h3>
            <button
              onClick={() => onTriggerAgent && onTriggerAgent('Data Miner')}
              className="w-full bg-white border border-[#c7c4d8] hover:border-[#3525cd] hover:text-[#3525cd] p-3 rounded text-left transition-colors flex items-center gap-3"
            >
              <span className="material-symbols-outlined text-[#3525cd]">travel_explore</span>
              <div>
                <span className="block font-semibold text-xs text-[#1a1c1c]">Run Data Miner Alpha</span>
                <span className="text-[10px] text-[#777587]">Scrape market competitor matrices</span>
              </div>
            </button>
            <button
              onClick={() => onTriggerAgent && onTriggerAgent('Draftsmith')}
              className="w-full bg-white border border-[#c7c4d8] hover:border-[#3525cd] hover:text-[#3525cd] p-3 rounded text-left transition-colors flex items-center gap-3"
            >
              <span className="material-symbols-outlined text-[#3525cd]">edit_document</span>
              <div>
                <span className="block font-semibold text-xs text-[#1a1c1c]">Synthesize Executive Draft</span>
                <span className="text-[10px] text-[#777587]">Transform research into report sections</span>
              </div>
            </button>
            <button
              onClick={() => onTriggerAgent && onTriggerAgent('Re-index Knowledge Base')}
              className="w-full bg-white border border-[#c7c4d8] hover:border-[#3525cd] hover:text-[#3525cd] p-3 rounded text-left transition-colors flex items-center gap-3"
            >
              <span className="material-symbols-outlined text-[#3525cd]">sync</span>
              <div>
                <span className="block font-semibold text-xs text-[#1a1c1c]">Re-Index Knowledge Base</span>
                <span className="text-[10px] text-[#777587]">Reparse PDF and Markdown sources</span>
              </div>
            </button>
          </div>
        )}

        {activeTab === 'sources' && (
          <div className="space-y-3">
            <h3 className="font-label-caps text-[11px] text-[#777587] uppercase tracking-widest">
              Cited Document Sources
            </h3>
            <div className="p-3 bg-white border border-[#c7c4d8] rounded flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-red-600 text-lg">picture_as_pdf</span>
                <div>
                  <span className="block font-semibold text-xs text-[#1a1c1c]">Q2-Performance.pdf</span>
                  <span className="text-[10px] text-[#777587]">Internal Audit Ref #A4</span>
                </div>
              </div>
              <span className="text-[10px] bg-[#3525cd]/10 text-[#3525cd] px-1.5 py-0.5 rounded">Verified</span>
            </div>
            <div className="p-3 bg-white border border-[#c7c4d8] rounded flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-emerald-600 text-lg">csv</span>
                <div>
                  <span className="block font-semibold text-xs text-[#1a1c1c]">Competitor_Pricing_2023.csv</span>
                  <span className="text-[10px] text-[#777587]">Market Research</span>
                </div>
              </div>
              <span className="text-[10px] bg-[#3525cd]/10 text-[#3525cd] px-1.5 py-0.5 rounded">Indexed</span>
            </div>
            <div className="p-3 bg-white border border-[#c7c4d8] rounded flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-blue-600 text-lg">code</span>
                <div>
                  <span className="block font-semibold text-xs text-[#1a1c1c]">API_Documentation_v2.md</span>
                  <span className="text-[10px] text-[#777587]">Technical Specs</span>
                </div>
              </div>
              <span className="text-[10px] bg-[#3525cd]/10 text-[#3525cd] px-1.5 py-0.5 rounded">Indexed</span>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};
