import React from 'react';
import { NavigationTab } from '../types';
import { USER_AVATAR, COLLABORATOR_1, COLLABORATOR_2 } from '../data';

interface TopAppBarProps {
  currentTab: NavigationTab;
  onExport: () => void;
  onShare: () => void;
  toggleInspector?: () => void;
  inspectorOpen?: boolean;
  documentTitle?: string;
  exportEnabled?: boolean;
}

export const TopAppBar: React.FC<TopAppBarProps> = ({
  currentTab,
  onExport,
  onShare,
  toggleInspector,
  inspectorOpen,
  documentTitle,
  exportEnabled = true
}) => {
  return (
    <header className="glass-header fixed top-0 right-0 left-16 h-14 sm:h-16 flex justify-between items-center px-3 sm:px-8 z-40 transition-all">
      <div className="flex items-center gap-3">
        {currentTab === 'editor' && (
          <span className="material-symbols-outlined text-[#464555] text-xl">
            description
          </span>
        )}

        <span className="font-headline-sm text-headline-sm font-bold text-[#1a1c1c] tracking-tight truncate max-w-[45vw] sm:max-w-none">
          {currentTab === 'forge' && 'Nexus Forge'}
          {currentTab === 'flow' && (
            <>
              Nexus <span className="font-normal text-[#464555]/70 text-lg ml-2">| Flow Visualization</span>
            </>
          )}
          {currentTab === 'editor' && (documentTitle || 'Report document')}
          {currentTab === 'library' && 'Knowledge Base'}
          {currentTab === 'archive' && 'Archive'}
        </span>

        {currentTab === 'editor' && (
          <>
            <div className="h-4 w-px bg-[#c7c4d8] mx-2"></div>
            <span className="font-label-caps text-label-caps text-[#777587] bg-[#eeeeed] px-2 py-0.5 rounded">
              Draft
            </span>
          </>
        )}
      </div>

      <div className="flex items-center gap-1.5 sm:gap-4">
        {currentTab === 'editor' && (
          <div className="flex -space-x-2 mr-2">
            <img
              src={COLLABORATOR_1}
              alt="Collaborator 1"
              className="w-8 h-8 rounded-full border-2 border-white object-cover shadow-xs"
            />
            <img
              src={COLLABORATOR_2}
              alt="Collaborator 2"
              className="w-8 h-8 rounded-full border-2 border-white object-cover shadow-xs"
            />
            <div className="w-8 h-8 rounded-full border-2 border-white bg-[#e2e2e2] flex items-center justify-center font-label-caps text-[10px] text-[#464555]">
              +2
            </div>
          </div>
        )}

        {/* Share Button */}
        <button
          onClick={onShare}
          className="text-[#464555] hover:text-[#3525cd] transition-colors font-body-md text-body-md flex items-center gap-1.5 sm:gap-2 px-2 sm:px-3 py-1.5 rounded hover:bg-[#e8e8e7]"
          title="Copy task reference"
        >
          <span className="material-symbols-outlined text-[18px]">share</span>
          <span className="hidden sm:inline">Share</span>
        </button>

        {/* Export Button */}
        <button
          onClick={onExport}
          disabled={!exportEnabled}
          title={exportEnabled ? 'Export review-gated DOCX' : 'Export is available after review succeeds'}
          className="bg-[#3525cd] text-white px-2.5 sm:px-4 py-2 rounded font-body-md text-body-md hover:bg-[#4f46e5] transition-colors flex items-center gap-1.5 sm:gap-2 shadow-xs active:scale-98 disabled:cursor-not-allowed disabled:bg-[#b8b5cf] disabled:text-white/80 disabled:shadow-none"
        >
          <span className="material-symbols-outlined text-[18px]">download</span>
          <span className="hidden sm:inline">Export</span>
        </button>

        {/* Toggle Inspector (For Flow & Editor views) */}
        {(currentTab === 'flow' || currentTab === 'editor') && toggleInspector && (
          <button
            onClick={toggleInspector}
            className={`p-2 rounded border transition-colors ${
              inspectorOpen
                ? 'bg-[#3525cd]/10 border-[#3525cd] text-[#3525cd]'
                : 'border-[#c7c4d8] text-[#464555] hover:bg-[#e8e8e7]'
            }`}
            title="Toggle Inspector Panel"
          >
            <span className="material-symbols-outlined text-[20px]">view_sidebar</span>
          </button>
        )}

        {/* User Profile Avatar */}
        <div className="hidden sm:block w-8 h-8 rounded-full overflow-hidden border border-[#c7c4d8] ml-2">
          <img
            src={USER_AVATAR}
            alt="User Avatar"
            className="w-full h-full object-cover"
          />
        </div>
      </div>
    </header>
  );
};
