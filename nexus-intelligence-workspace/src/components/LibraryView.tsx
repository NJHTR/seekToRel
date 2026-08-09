import React, { useState, useRef } from 'react';
import { KnowledgeDocument } from '../types';

interface LibraryViewProps {
  knowledgeDocs: KnowledgeDocument[];
  onAddKnowledgeDoc: (doc: KnowledgeDocument) => void;
}

export const LibraryView: React.FC<LibraryViewProps> = ({
  knowledgeDocs,
  onAddKnowledgeDoc
}) => {
  const [selectedCollection, setSelectedCollection] = useState<string>('All Documents');
  const [folderQuery, setFolderQuery] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const collections = [
    { name: 'All Documents', count: 142 },
    { name: 'Market Research', count: 45 },
    { name: 'Legal Precedents', count: 12 },
    { name: 'Technical Specs', count: 28 },
  ];

  const agentSources = [
    { name: 'CRM Integration', icon: 'grid_view' },
    { name: 'Cloud Drive', icon: 'cloud' },
  ];

  const filteredDocs = knowledgeDocs.filter((doc) => {
    if (selectedCollection !== 'All Documents' && doc.collection !== selectedCollection) {
      return false;
    }
    return doc.name.toLowerCase().includes(folderQuery.toLowerCase());
  });

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const file = e.target.files[0];
      const newDoc: KnowledgeDocument = {
        id: `k-${Date.now()}`,
        name: file.name,
        collection: selectedCollection === 'All Documents' ? 'Market Research' : selectedCollection,
        dateAdded: 'Just now',
        status: 'ANALYZING'
      };
      onAddKnowledgeDoc(newDoc);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      const newDoc: KnowledgeDocument = {
        id: `k-${Date.now()}`,
        name: file.name,
        collection: selectedCollection === 'All Documents' ? 'Market Research' : selectedCollection,
        dateAdded: 'Just now',
        status: 'ANALYZING'
      };
      onAddKnowledgeDoc(newDoc);
    }
  };

  return (
    <div className="pt-20 pb-20 ml-16 min-h-screen flex animate-fade-in w-full">
      {/* Left Collections Sub-Sidebar */}
      <aside className="w-64 border-r border-[#c7c4d8] bg-[#f3f4f3] p-6 flex flex-col justify-between shrink-0">
        <div>
          <h2 className="font-headline-sm text-headline-sm text-[#1a1c1c] mb-6">
            Knowledge Base
          </h2>

          {/* Folder Search */}
          <div className="relative mb-6">
            <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-[#777587] text-lg">
              search
            </span>
            <input
              type="text"
              value={folderQuery}
              onChange={(e) => setFolderQuery(e.target.value)}
              placeholder="Search folders..."
              className="w-full bg-white border border-[#c7c4d8] rounded px-3 py-1.5 pl-8 text-xs font-body-md text-[#1a1c1c] outline-none focus:border-[#3525cd]"
            />
          </div>

          {/* Collections List */}
          <div className="mb-8">
            <h3 className="font-label-caps text-[10px] text-[#777587] uppercase tracking-wider mb-3">
              Collections
            </h3>
            <div className="space-y-1">
              {collections.map((col) => (
                <button
                  key={col.name}
                  onClick={() => setSelectedCollection(col.name)}
                  className={`w-full flex items-center justify-between px-3 py-2 rounded text-xs font-body-md transition-colors ${
                    selectedCollection === col.name
                      ? 'bg-[#e2e2e2] font-semibold text-[#1a1c1c]'
                      : 'hover:bg-[#e8e8e7] text-[#464555]'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[#3525cd] text-base">
                      folder
                    </span>
                    <span>{col.name}</span>
                  </div>
                  <span className="font-label-caps text-[10px] text-[#777587]">
                    {col.count}
                  </span>
                </button>
              ))}
            </div>
          </div>

          {/* Agent Sources */}
          <div>
            <h3 className="font-label-caps text-[10px] text-[#777587] uppercase tracking-wider mb-3">
              Agent Sources
            </h3>
            <div className="space-y-1">
              {agentSources.map((src) => (
                <div
                  key={src.name}
                  className="flex items-center gap-2 px-3 py-2 text-xs text-[#464555] hover:bg-[#e8e8e7] rounded cursor-pointer transition-colors"
                >
                  <span className="material-symbols-outlined text-base text-[#777587]">
                    {src.icon}
                  </span>
                  <span>{src.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* New Folder Action */}
        <button className="w-full mt-auto py-2 border border-[#c7c4d8] rounded bg-white text-xs font-body-md font-semibold text-[#1a1c1c] hover:bg-[#f9f9f8] transition-colors flex items-center justify-center gap-2">
          <span className="material-symbols-outlined text-base">add</span>
          New Folder
        </button>
      </aside>

      {/* Main Panel */}
      <main className="flex-1 p-8 overflow-y-auto">
        <div className="max-w-[1000px] mx-auto space-y-8">
          {/* File Dropzone Ingestion Area */}
          <div
            onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`border-2 border-dashed rounded-lg p-10 text-center cursor-pointer transition-all ${
              isDragging
                ? 'border-[#3525cd] bg-[#3525cd]/5 scale-[1.01]'
                : 'border-[#c7c4d8] bg-[#f9f9f8] hover:border-[#3525cd] hover:bg-white'
            }`}
          >
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              className="hidden"
            />
            <div className="w-12 h-12 bg-[#e2e2e2] rounded-lg flex items-center justify-center mx-auto mb-4 text-[#464555]">
              <span className="material-symbols-outlined text-2xl">upload_file</span>
            </div>
            <h3 className="font-headline-sm text-headline-sm text-[#1a1c1c] mb-2">
              New Ingestion
            </h3>
            <p className="font-body-md text-sm text-[#464555] max-w-md mx-auto">
              Drag and drop files here, or click to browse. Supported formats: PDF, DOCX, TXT, CSV. Agents will automatically begin processing upon upload.
            </p>
          </div>

          {/* All Documents Section */}
          <div className="space-y-4">
            <div className="flex items-center justify-between border-b border-[#c7c4d8] pb-3">
              <h2 className="font-headline-sm text-headline-sm text-[#1a1c1c]">
                {selectedCollection}
              </h2>

              <div className="flex gap-2">
                <button className="px-3 py-1.5 bg-[#e8e8e7] border border-[#c7c4d8] rounded font-label-caps text-xs text-[#464555] flex items-center gap-1.5 hover:bg-[#e2e2e2]">
                  <span className="material-symbols-outlined text-sm">filter_alt</span>
                  Filter
                </button>
                <button className="px-3 py-1.5 bg-[#e8e8e7] border border-[#c7c4d8] rounded font-label-caps text-xs text-[#464555] flex items-center gap-1.5 hover:bg-[#e2e2e2]">
                  <span className="material-symbols-outlined text-sm">sort</span>
                  Sort
                </button>
              </div>
            </div>

            {/* Documents Table */}
            <div className="bg-white border border-[#c7c4d8] rounded-lg overflow-hidden shadow-2xs">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-[#c7c4d8] bg-[#f3f4f3] font-label-caps text-[11px] text-[#777587]">
                    <th className="p-4 font-normal">DOCUMENT NAME</th>
                    <th className="p-4 font-normal">COLLECTION</th>
                    <th className="p-4 font-normal">DATE ADDED</th>
                    <th className="p-4 font-normal">AGENT STATUS</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#c7c4d8]/50 font-body-md text-sm">
                  {filteredDocs.map((doc) => (
                    <tr key={doc.id} className="hover:bg-[#f9f9f8] transition-colors">
                      <td className="p-4 flex items-center gap-3">
                        <span className="material-symbols-outlined text-[#777587]">
                          description
                        </span>
                        <span className="font-semibold text-[#1a1c1c]">{doc.name}</span>
                      </td>
                      <td className="p-4">
                        <span className="bg-[#e8e8e7] text-[#464555] font-label-caps text-[10px] px-2.5 py-1 rounded">
                          {doc.collection}
                        </span>
                      </td>
                      <td className="p-4 text-[#777587] text-xs font-label-caps">
                        {doc.dateAdded}
                      </td>
                      <td className="p-4">
                        {doc.status === 'ANALYZING' && (
                          <span className="inline-flex items-center gap-1.5 text-[#3525cd] font-label-caps text-[10px] font-bold uppercase tracking-wider">
                            <span className="w-2 h-2 rounded-full bg-[#3525cd] animate-pulse"></span>
                            ANALYZING
                          </span>
                        )}
                        {doc.status === 'INDEXED' && (
                          <span className="inline-flex items-center gap-1.5 text-emerald-800 font-label-caps text-[10px] font-bold uppercase tracking-wider">
                            <span className="w-2 h-2 rounded-full bg-emerald-600"></span>
                            INDEXED
                          </span>
                        )}
                        {doc.status === 'PARSING FAILED' && (
                          <span className="inline-flex items-center gap-1.5 text-red-700 font-label-caps text-[10px] font-bold uppercase tracking-wider">
                            <span className="material-symbols-outlined text-sm">error</span>
                            PARSING FAILED
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};
