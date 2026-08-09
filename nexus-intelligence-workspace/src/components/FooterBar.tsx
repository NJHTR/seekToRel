import React from 'react';

interface FooterBarProps {
  hasRightSidebar?: boolean;
}

export const FooterBar: React.FC<FooterBarProps> = ({ hasRightSidebar = false }) => {
  return (
    <footer className={`fixed bottom-0 left-16 ${hasRightSidebar ? 'lg:right-80 right-0' : 'right-0'} h-8 flex justify-between items-center px-4 z-20 bg-[#ffffff] border-t border-[#c7c4d8] opacity-85 hover:opacity-100 transition-opacity text-xs font-label-caps text-[#464555]`}>
      <div className="flex items-center gap-2">
        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
        <span>Connected to Agent Engine</span>
      </div>
      <div className="flex items-center gap-4">
        <span className="hover:text-[#3525cd] cursor-pointer transition-colors flex items-center gap-1">
          Syncing...
        </span>
        <span className="hover:text-[#3525cd] cursor-pointer transition-colors">v2.4.0</span>
      </div>
    </footer>
  );
};
