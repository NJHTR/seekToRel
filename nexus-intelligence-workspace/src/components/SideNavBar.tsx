import React from 'react';
import { NavigationTab } from '../types';
import { USER_AVATAR } from '../data';

interface SideNavBarProps {
  currentTab: NavigationTab;
  onTabChange: (tab: NavigationTab) => void;
}

export const SideNavBar: React.FC<SideNavBarProps> = ({ currentTab, onTabChange }) => {
  return (
    <nav className="docked h-screen w-16 left-0 top-0 border-r border-[#c7c4d8] bg-[#f9f9f8] fixed flex flex-col items-center py-8 z-50">
      {/* Brand Hexagon / Logo */}
      <div 
        className="mb-8 cursor-pointer group"
        onClick={() => onTabChange('forge')}
        title="Nexus Home"
      >
        <span className="material-symbols-outlined text-[#3525cd] text-3xl font-bold group-hover:scale-110 transition-transform">
          hexagon
        </span>
      </div>

      {/* Navigation Tabs */}
      <div className="flex flex-col gap-6 w-full items-center flex-1">
        {/* Forge (Home) */}
        <button
          onClick={() => onTabChange('forge')}
          className={`w-full flex justify-center py-3 border-l-2 transition-colors duration-200 relative group ${
            currentTab === 'forge'
              ? 'text-[#3525cd] border-[#3525cd] bg-[#eeeeed]/60'
              : 'text-[#464555] border-transparent hover:bg-[#e8e8e7]'
          }`}
          title="Forge"
        >
          <span className="material-symbols-outlined text-[24px]">home_work</span>
          <div className="absolute left-16 bg-[#e2e2e2] px-3 py-1 rounded text-label-caps opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-50 shadow-sm border border-[#c7c4d8]">
            Forge
          </div>
        </button>

        {/* Library (Knowledge Base) */}
        <button
          onClick={() => onTabChange('library')}
          className={`w-full flex justify-center py-3 border-l-2 transition-colors duration-200 relative group ${
            currentTab === 'library'
              ? 'text-[#3525cd] border-[#3525cd] bg-[#eeeeed]/60'
              : 'text-[#464555] border-transparent hover:bg-[#e8e8e7]'
          }`}
          title="Library"
        >
          <span className="material-symbols-outlined text-[24px]">auto_stories</span>
          <div className="absolute left-16 bg-[#e2e2e2] px-3 py-1 rounded text-label-caps opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-50 shadow-sm border border-[#c7c4d8]">
            Library
          </div>
        </button>

        {/* Flow (Visualization) */}
        <button
          onClick={() => onTabChange('flow')}
          className={`w-full flex justify-center py-3 border-l-2 transition-colors duration-200 relative group ${
            currentTab === 'flow'
              ? 'text-[#3525cd] border-[#3525cd] bg-[#3525cd]/5'
              : 'text-[#464555] border-transparent hover:bg-[#e8e8e7]'
          }`}
          title="Flow"
        >
          <span className="material-symbols-outlined text-[24px]">account_tree</span>
          <div className="absolute left-16 bg-[#e2e2e2] px-3 py-1 rounded text-label-caps opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-50 shadow-sm border border-[#c7c4d8]">
            Flow
          </div>
        </button>

        {/* Document Editor */}
        <button
          onClick={() => onTabChange('editor')}
          className={`w-full flex justify-center py-3 border-l-2 transition-colors duration-200 relative group ${
            currentTab === 'editor'
              ? 'text-[#3525cd] border-[#3525cd] bg-[#3525cd]/5'
              : 'text-[#464555] border-transparent hover:bg-[#e8e8e7]'
          }`}
          title="Document Workspace"
        >
          <span className="material-symbols-outlined text-[24px]">description</span>
          <div className="absolute left-16 bg-[#e2e2e2] px-3 py-1 rounded text-label-caps opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-50 shadow-sm border border-[#c7c4d8]">
            Editor
          </div>
        </button>

        {/* Archive */}
        <button
          onClick={() => onTabChange('archive')}
          className={`w-full flex justify-center py-3 border-l-2 transition-colors duration-200 relative group ${
            currentTab === 'archive'
              ? 'text-[#3525cd] border-[#3525cd] bg-[#eeeeed]/60'
              : 'text-[#464555] border-transparent hover:bg-[#e8e8e7]'
          }`}
          title="Archive"
        >
          <span className="material-symbols-outlined text-[24px]">archive</span>
          <div className="absolute left-16 bg-[#e2e2e2] px-3 py-1 rounded text-label-caps opacity-0 group-hover:opacity-100 pointer-events-none transition-opacity whitespace-nowrap z-50 shadow-sm border border-[#c7c4d8]">
            Archive
          </div>
        </button>
      </div>

      {/* User Profile Footer Avatar */}
      <div className="mt-auto">
        <div className="w-9 h-9 rounded-full overflow-hidden border border-[#c7c4d8] hover:border-[#3525cd] transition-colors cursor-pointer">
          <img
            src={USER_AVATAR}
            alt="User Profile"
            className="w-full h-full object-cover"
          />
        </div>
      </div>
    </nav>
  );
};
