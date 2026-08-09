import React, { useState } from 'react';
import { PipelineRow } from '../types';

interface FlowViewProps {
  pipelines: PipelineRow[];
  onSelectTask?: (taskName: string) => void;
}

export const FlowView: React.FC<FlowViewProps> = ({ pipelines, onSelectTask }) => {
  const [selectedTask, setSelectedTask] = useState<string>('Semantic Analysis');

  const handleTaskClick = (taskName: string) => {
    setSelectedTask(taskName);
    if (onSelectTask) {
      onSelectTask(taskName);
    }
  };

  return (
    <div className="pt-24 pb-20 md:pl-24 pr-4 md:pr-8 min-h-screen animate-fade-in max-w-[1200px] mx-auto w-full">
      {/* Page Header */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-2">
          <h2 className="font-headline-md text-headline-md text-[#1a1c1c]">
            Global Strategy Synthesis
          </h2>
          <div className="flex items-center gap-2 bg-[#e8e8e7] px-3 py-1.5 rounded-sm border border-[#c7c4d8]">
            <span className="w-2 h-2 rounded-full bg-[#3525cd] animate-pulse"></span>
            <span className="font-label-caps text-label-caps text-[#464555]">System Active</span>
          </div>
        </div>
        <p className="font-body-md text-body-md text-[#464555] max-w-2xl">
          Visualizing concurrent agent pipelines aggregating market intelligence, synthesizing structural data, and structuring final documentation.
        </p>
      </div>

      {/* Gantt / Pipeline Canvas */}
      <div className="bg-white border border-[#c7c4d8] rounded-lg overflow-hidden relative shadow-2xs">
        {/* Time Scale Header */}
        <div className="flex border-b border-[#c7c4d8] bg-[#f3f4f3]/60 sticky top-0 z-10 font-label-caps text-label-caps text-[#464555]">
          <div className="w-64 flex-shrink-0 p-4 border-r border-[#c7c4d8] flex items-center justify-between">
            <span>Agent Pipeline</span>
            <span className="material-symbols-outlined text-[16px]">filter_list</span>
          </div>
          <div className="flex-grow flex">
            <div className="flex-1 p-2 border-r border-[#c7c4d8]/40 text-center relative">
              <span className="absolute top-2 left-2 text-[#777587]">T-0</span>
            </div>
            <div className="flex-1 p-2 border-r border-[#c7c4d8]/40 text-center relative">
              <span className="absolute top-2 left-2 text-[#777587]">T+1</span>
            </div>
            <div className="flex-1 p-2 border-r border-[#c7c4d8]/40 text-center relative">
              <span className="absolute top-2 left-2 text-[#777587]">T+2</span>
            </div>
            <div className="flex-1 p-2 border-r border-[#c7c4d8]/40 text-center relative bg-[#3525cd]/5">
              <span className="absolute top-2 left-2 text-[#3525cd] font-bold">Now</span>
            </div>
            <div className="flex-1 p-2 border-r border-[#c7c4d8]/40 text-center relative">
              <span className="absolute top-2 left-2 text-[#777587]">T+4</span>
            </div>
            <div className="flex-1 p-2 text-center relative">
              <span className="absolute top-2 left-2 text-[#777587]">T+5</span>
            </div>
          </div>
        </div>

        {/* Pipeline Rows */}
        <div className="relative w-full min-h-[460px]">
          {/* Vertical Grid Lines */}
          <div className="absolute inset-0 flex pointer-events-none z-0 opacity-20">
            <div className="w-64 flex-shrink-0 border-r border-[#c7c4d8]"></div>
            <div className="flex-grow flex">
              <div className="flex-1 border-r border-[#c7c4d8] border-dashed"></div>
              <div className="flex-1 border-r border-[#c7c4d8] border-dashed"></div>
              <div className="flex-1 border-r border-[#c7c4d8] border-dashed"></div>
              <div className="flex-1 border-r border-[#3525cd] border-solid relative">
                <div className="absolute top-0 bottom-0 left-0 w-[2px] bg-[#3525cd] shadow-[0_0_8px_rgba(53,37,205,0.4)]"></div>
              </div>
              <div className="flex-1 border-r border-[#c7c4d8] border-dashed"></div>
              <div className="flex-1"></div>
            </div>
          </div>

          {/* Row 1: Research Agent */}
          <div className="flex border-b border-[#c7c4d8]/50 hover:bg-[#f3f4f3]/40 transition-colors relative z-10">
            <div className="w-64 flex-shrink-0 p-4 border-r border-[#c7c4d8] bg-white">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded bg-[#eeeeed] border border-[#c7c4d8] flex items-center justify-center text-[#645d58]">
                  <span className="material-symbols-outlined text-[18px]">travel_explore</span>
                </div>
                <div>
                  <h4 className="font-body-md text-body-md font-medium text-[#1a1c1c] leading-tight">
                    Research Agent
                  </h4>
                  <span className="font-label-caps text-label-caps text-[#777587] text-[10px]">
                    ID: RX-992
                  </span>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 mt-3">
                <span className="px-2 py-0.5 rounded-sm bg-[#e8e8e7] text-[#464555] font-label-caps text-[10px] border border-[#c7c4d8]/50">
                  Data Mining
                </span>
                <span className="px-2 py-0.5 rounded-sm bg-[#3525cd]/10 text-[#3525cd] font-label-caps text-[10px] border border-[#3525cd]/20 font-semibold">
                  Active
                </span>
              </div>
            </div>

            <div className="flex-grow relative p-4 flex items-center min-h-[100px]">
              {/* Task 1: Initial Scrape */}
              <div
                onClick={() => handleTaskClick('Initial Scrape')}
                className={`absolute left-4 w-[24%] h-12 bg-[#eeeeed] rounded border flex flex-col justify-center px-3 cursor-pointer transition-all shadow-2xs ${
                  selectedTask === 'Initial Scrape' ? 'border-[#3525cd] ring-1 ring-[#3525cd]' : 'border-[#c7c4d8] hover:border-[#645d58]'
                }`}
              >
                <div className="flex justify-between items-center mb-1">
                  <span className="font-label-caps text-label-caps text-[#1a1c1c] font-semibold text-[10px] truncate">
                    Initial Scrape
                  </span>
                  <span className="material-symbols-outlined text-[14px] text-emerald-600">
                    check_circle
                  </span>
                </div>
                <div className="w-full bg-[#c7c4d8] h-1 rounded-full overflow-hidden">
                  <div className="bg-[#645d58] w-full h-full"></div>
                </div>
              </div>

              {/* Connector */}
              <div className="absolute left-[calc(24%+20px)] w-[8%] h-[1px] bg-[#c7c4d8] top-1/2 -translate-y-1/2"></div>

              {/* Task 2: Semantic Analysis */}
              <div
                onClick={() => handleTaskClick('Semantic Analysis')}
                className={`absolute left-[calc(32%+28px)] w-[32%] h-16 bg-white border border-[#3525cd]/40 agent-processing rounded shadow-2xs flex items-start gap-2 p-2 cursor-pointer relative overflow-hidden group ${
                  selectedTask === 'Semantic Analysis' ? 'ring-2 ring-[#3525cd]' : ''
                }`}
              >
                <div className="absolute left-0 top-0 bottom-0 w-1 bg-[#3525cd] flow-line-active"></div>
                <span className="material-symbols-outlined text-[16px] text-[#3525cd] mt-0.5 ml-1">
                  psychology
                </span>
                <div className="flex-grow min-w-0">
                  <div className="flex justify-between items-start mb-1">
                    <span className="font-label-caps text-label-caps text-[#3525cd] font-semibold text-[10px] truncate">
                      Semantic Analysis
                    </span>
                    <span className="font-label-caps text-[9px] text-[#777587]">Conf: 94%</span>
                  </div>
                  <p className="font-body-md text-[11px] leading-tight text-[#464555] truncate">
                    Processing latest quarterly reports...
                  </p>
                  <div className="w-full bg-[#3525cd]/10 h-1 rounded-full overflow-hidden mt-1.5">
                    <div className="bg-[#3525cd] w-[70%] h-full"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Row 2: Synthesis Agent */}
          <div className="flex border-b border-[#c7c4d8]/50 hover:bg-[#f3f4f3]/40 transition-colors relative z-10">
            <div className="w-64 flex-shrink-0 p-4 border-r border-[#c7c4d8] bg-white">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded bg-[#eeeeed] border border-[#c7c4d8] flex items-center justify-center text-[#645d58]">
                  <span className="material-symbols-outlined text-[18px]">hub</span>
                </div>
                <div>
                  <h4 className="font-body-md text-body-md font-medium text-[#1a1c1c] leading-tight">
                    Synthesis Agent
                  </h4>
                  <span className="font-label-caps text-label-caps text-[#777587] text-[10px]">
                    ID: SY-401
                  </span>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 mt-3">
                <span className="px-2 py-0.5 rounded-sm bg-[#e8e8e7] text-[#464555] font-label-caps text-[10px] border border-[#c7c4d8]/50">
                  Structuring
                </span>
                <span className="px-2 py-0.5 rounded-sm bg-[#e2e2e2] text-[#645d58] font-label-caps text-[10px] border border-[#c7c4d8]">
                  Waiting
                </span>
              </div>
            </div>

            <div className="flex-grow relative p-4 flex items-center min-h-[90px]">
              {/* Dependency Curve Line */}
              <svg className="absolute left-[calc(50%+16px)] top-[-40px] w-8 h-[60px] pointer-events-none" preserveAspectRatio="none">
                <path d="M 0 0 C 16 0, 16 60, 32 60" fill="none" stroke="#3525cd" strokeOpacity="0.4" strokeDasharray="4" strokeWidth="1.5" />
              </svg>

              {/* Task Block (Pending) */}
              <div
                onClick={() => handleTaskClick('Draft Assembly')}
                className="absolute left-[calc(64%+16px)] w-[22%] h-12 bg-white rounded border border-[#c7c4d8] border-dashed flex flex-col justify-center px-3 cursor-pointer opacity-80 hover:opacity-100 transition-opacity"
              >
                <div className="flex justify-between items-center mb-1">
                  <span className="font-label-caps text-label-caps text-[#464555] font-semibold text-[10px] truncate">
                    Draft Assembly
                  </span>
                  <span className="material-symbols-outlined text-[14px] text-[#777587]">
                    schedule
                  </span>
                </div>
                <div className="w-full bg-[#c7c4d8]/30 h-1 rounded-full overflow-hidden">
                  <div className="bg-[#645d58] w-0 h-full"></div>
                </div>
              </div>
            </div>
          </div>

          {/* Row 3: Review Agent */}
          <div className="flex border-b border-[#c7c4d8]/50 hover:bg-[#f3f4f3]/40 transition-colors relative z-10">
            <div className="w-64 flex-shrink-0 p-4 border-r border-[#c7c4d8] bg-white">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded bg-[#eeeeed] border border-[#c7c4d8] flex items-center justify-center text-[#645d58]">
                  <span className="material-symbols-outlined text-[18px]">fact_check</span>
                </div>
                <div>
                  <h4 className="font-body-md text-body-md font-medium text-[#1a1c1c] leading-tight">
                    Review Agent
                  </h4>
                  <span className="font-label-caps text-label-caps text-[#777587] text-[10px]">
                    ID: RV-008
                  </span>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 mt-3">
                <span className="px-2 py-0.5 rounded-sm bg-[#e8e8e7] text-[#464555] font-label-caps text-[10px] border border-[#c7c4d8]/50">
                  Validation
                </span>
              </div>
            </div>

            <div className="flex-grow relative p-4 flex items-center min-h-[90px]">
              <div className="absolute left-[calc(85%+20px)] w-[12%] h-12 bg-white rounded border border-[#c7c4d8] border-dashed flex flex-col justify-center px-3 opacity-60">
                <div className="flex justify-center items-center">
                  <span className="font-label-caps text-label-caps text-[#464555] text-[10px] truncate">
                    Compliance Check
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Row 4: External API Call */}
          <div className="flex border-b border-[#c7c4d8]/50 hover:bg-[#f3f4f3]/40 transition-colors relative z-10">
            <div className="w-64 flex-shrink-0 p-4 border-r border-[#c7c4d8] bg-white">
              <div className="flex items-center gap-3 mb-2">
                <div className="w-8 h-8 rounded bg-[#f9f9f8] border border-[#c7c4d8] flex items-center justify-center text-[#645d58]">
                  <span className="material-symbols-outlined text-[18px]">api</span>
                </div>
                <div>
                  <h4 className="font-body-md text-[14px] font-medium text-[#1a1c1c] leading-tight">
                    Market Data API
                  </h4>
                  <span className="font-label-caps text-label-caps text-[#777587] text-[10px]">
                    External
                  </span>
                </div>
              </div>
            </div>

            <div className="flex-grow relative p-4 flex items-center min-h-[80px]">
              <div className="absolute left-[calc(30%+16px)] w-[16%] h-10 bg-[#f9f9f8] rounded border border-[#c7c4d8] flex flex-col justify-center px-3 shadow-2xs">
                <div className="flex justify-between items-center">
                  <span className="font-label-caps text-label-caps text-[#464555] text-[9px] truncate">
                    Fetching Indexes
                  </span>
                  <span className="w-1.5 h-1.5 rounded-full bg-[#3525cd] animate-pulse"></span>
                </div>
              </div>

              {/* Vertical API Connection Line to Research Agent */}
              <svg className="absolute left-[calc(38%+16px)] top-[-110px] w-[2px] h-[110px] pointer-events-none" preserveAspectRatio="none">
                <line x1="1" y1="0" x2="1" y2="110" stroke="#3525cd" strokeWidth="1.5" className="flow-line-active" />
                <circle cx="1" cy="0" r="3" fill="#3525cd" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Detail Bento Cards */}
      <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Focus Area Card */}
        <div className="bg-white border border-[#c7c4d8] rounded-lg p-5 shadow-2xs">
          <h3 className="font-label-caps text-label-caps text-[#777587] mb-3 flex items-center gap-2 uppercase tracking-wider">
            <span className="material-symbols-outlined text-[16px]">radar</span> Focus Area
          </h3>
          <p className="font-body-lg text-body-lg text-[#1a1c1c] mb-2 font-semibold">
            {selectedTask === 'Semantic Analysis' ? 'Semantic Analysis of Q3 Reports' : selectedTask}
          </p>
          <p className="font-body-md text-[14px] text-[#464555]">
            Research agent is currently isolating key forward-looking statements regarding supply chain resilience across top 5 competitors.
          </p>
        </div>

        {/* Pipeline Metrics */}
        <div className="bg-white border border-[#c7c4d8] rounded-lg p-5 shadow-2xs flex flex-col justify-between">
          <h3 className="font-label-caps text-label-caps text-[#777587] mb-3 flex items-center gap-2 uppercase tracking-wider">
            <span className="material-symbols-outlined text-[16px]">analytics</span> Pipeline Metrics
          </h3>
          <div className="flex justify-between items-end">
            <div>
              <span className="block font-display-lg text-[32px] font-bold text-[#1a1c1c] leading-none mb-1">
                94%
              </span>
              <span className="font-label-caps text-[10px] text-[#777587]">Avg Confidence</span>
            </div>
            <div className="text-right">
              <span className="block font-display-lg text-[32px] font-bold text-[#1a1c1c] leading-none mb-1">
                12
              </span>
              <span className="font-label-caps text-[10px] text-[#777587]">Docs Processed</span>
            </div>
          </div>
        </div>

        {/* Output Snippet */}
        <div className="bg-white border border-[#c7c4d8] rounded-lg p-5 shadow-2xs relative overflow-hidden">
          <h3 className="font-label-caps text-label-caps text-[#777587] mb-2 flex justify-between items-center uppercase tracking-wider">
            <span className="flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px]">data_object</span> Last Output
            </span>
            <span className="text-[10px] text-[#464555] bg-[#e8e8e7] px-2 py-0.5 rounded font-mono">
              JSON
            </span>
          </h3>
          <div className="font-label-caps text-[11px] text-[#645d58] bg-[#f9f9f8] border border-[#c7c4d8]/40 p-2.5 rounded font-mono overflow-hidden">
            <pre className="m-0 whitespace-pre-wrap">
{`{
  "entity": "LogiCorp",
  "sentiment": 0.85,
  "key_drivers": [
    "Nearshoring",
    "Automated facilities"
  ]
}`}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
