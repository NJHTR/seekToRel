import React, { useState } from 'react';
import { DocumentItem } from '../types';

interface EditorViewProps {
  document: DocumentItem;
  onUpdateDocument: (updatedDoc: DocumentItem) => void;
}

export const EditorView: React.FC<EditorViewProps> = ({
  document,
  onUpdateDocument
}) => {
  const [showSuggestion, setShowSuggestion] = useState(true);
  const [cmdInput, setCmdInput] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [aiResponseText, setAiResponseText] = useState<string | null>(null);

  const docContent = document.content || {
    typeReport: 'Q3 STRATEGY REPORT',
    headline: 'Executive Summary: Market Dynamics',
    summaryText: 'The current fiscal quarter has demonstrated a significant shift in enterprise software adoption, primarily driven by the accelerated integration of generative AI models into core operational workflows. Initial analysis suggests a 45% reduction in time-to-market for early adopters.',
    suggestion: {
      id: 'sug-1',
      text: 'Consider citing the recent internal audit (Doc-Ref: Q2-Performance.pdf) which explicitly details the infrastructure costs associated with this adoption rate. It strengthens the fiscal argument.',
      docRef: 'Doc-Ref: Q2-Performance.pdf'
    },
    bodyParagraphs: [
      'Furthermore, competitive analysis indicates that incumbent market leaders are rapidly depreciating legacy technical debt to reallocate capital towards predictive analytics suites. This creates a critical window of opportunity for disruptive entrants targeting niche vertical integrations.'
    ],
    growthVectors: [
      { id: 'gv-1', title: 'Automated Compliance Monitoring (Projected 22% YoY)', completed: true },
      { id: 'gv-2', title: 'Synthetic Data Generation for ML Training', completed: true },
      { id: 'gv-3', title: 'Edge Computing Infrastructure (Awaiting Data)', completed: false }
    ]
  };

  // Accept Inline Suggestion
  const handleAcceptSuggestion = () => {
    setShowSuggestion(false);
    const updatedParagraphs = [
      ...docContent.bodyParagraphs,
      `[Cited Doc-Ref: Q2-Performance.pdf] Infrastructure analysis confirms that GPU cluster provisioning costs reduced by 38% under synthetic orchestration workflows.`
    ];

    onUpdateDocument({
      ...document,
      content: {
        ...docContent,
        bodyParagraphs: updatedParagraphs
      }
    });
  };

  // Toggle Growth Vector Completion
  const handleToggleVector = (id: string) => {
    const updatedVectors = docContent.growthVectors.map((v) =>
      v.id === id ? { ...v, completed: !v.completed } : v
    );

    onUpdateDocument({
      ...document,
      content: {
        ...docContent,
        growthVectors: updatedVectors
      }
    });
  };

  // Run CMD+K AI Prompt
  const handleRunCmdPrompt = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cmdInput.trim() || isGenerating) return;

    setIsGenerating(true);
    setAiResponseText(null);

    try {
      const res = await fetch('/api/agent/prompt', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: cmdInput,
          context: docContent.summaryText + '\n' + docContent.bodyParagraphs.join('\n'),
          docTitle: document.title
        })
      });

      const data = await res.json();
      const generated = data.response || 'Agent completed prompt execution.';
      setAiResponseText(generated);

      // Append generated response to document
      onUpdateDocument({
        ...document,
        content: {
          ...docContent,
          bodyParagraphs: [...docContent.bodyParagraphs, generated]
        }
      });
      setCmdInput('');
    } catch (err) {
      console.error('AI command error:', err);
    } finally {
      setIsGenerating(false);
    }
  };

  return (
    <div className="flex-1 ml-16 mr-80 pt-24 pb-32 px-12 lg:px-24 xl:px-36 relative min-h-screen animate-fade-in">
      {/* Document Paper Canvas */}
      <article className="bg-white rounded-xl border border-[#c7c4d8] shadow-2xs max-w-[800px] mx-auto p-10 lg:p-16 min-h-[780px] relative">
        <p className="font-label-caps text-label-caps text-[#777587] mb-6 uppercase tracking-widest">
          {docContent.typeReport}
        </p>

        <h2 className="font-headline-md text-headline-md text-[#1a1c1c] mb-6">
          {docContent.headline}
        </h2>

        <p className="font-body-lg text-body-lg text-[#464555] mb-6 leading-relaxed">
          {docContent.summaryText}
        </p>

        {/* Inline Agent Suggestion Card */}
        {showSuggestion && docContent.suggestion && (
          <div className="my-8 ml-4 pl-6 border-l-2 border-[#4f46e5] bg-[#f0f0ff] p-6 rounded-r-xl shadow-2xs relative group transition-all">
            <div className="absolute -left-[13px] top-6 w-6 h-6 rounded-full bg-[#3525cd] flex items-center justify-center shadow-xs">
              <span className="material-symbols-outlined text-white text-xs">
                psychology
              </span>
            </div>

            <div className="flex items-center justify-between mb-2">
              <span className="font-label-caps text-label-caps text-[#3525cd] tracking-widest uppercase font-bold">
                Agent Suggestion
              </span>
              <div className="flex gap-2">
                <button className="text-[#464555] hover:text-[#3525cd]">
                  <span className="material-symbols-outlined text-sm">thumb_up</span>
                </button>
                <button
                  onClick={() => setShowSuggestion(false)}
                  className="text-[#464555] hover:text-red-600"
                >
                  <span className="material-symbols-outlined text-sm">thumb_down</span>
                </button>
              </div>
            </div>

            <p className="font-body-md text-body-md text-[#464555] mb-4">
              {docContent.suggestion.text}
            </p>

            <div className="flex gap-3">
              <button
                onClick={handleAcceptSuggestion}
                className="font-label-caps text-label-caps bg-[#3525cd]/10 text-[#3525cd] px-3.5 py-1.5 rounded hover:bg-[#3525cd]/20 transition-colors cursor-pointer font-semibold"
              >
                Accept Revision
              </button>
              <button
                onClick={() => setShowSuggestion(false)}
                className="font-label-caps text-label-caps border border-[#c7c4d8] text-[#464555] px-3.5 py-1.5 rounded hover:bg-[#e8e8e7] transition-colors cursor-pointer"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}

        {/* Body Paragraphs */}
        {docContent.bodyParagraphs.map((para, idx) => (
          <p key={idx} className="font-body-lg text-body-lg text-[#464555] mb-6 leading-relaxed">
            {para}
          </p>
        ))}

        {/* Key Growth Vectors */}
        <h3 className="font-headline-sm text-headline-sm text-[#1a1c1c] mt-10 mb-4">
          Key Growth Vectors
        </h3>

        <ul className="list-none space-y-3 font-body-lg text-body-lg text-[#464555] ml-2">
          {docContent.growthVectors.map((vector) => (
            <li
              key={vector.id}
              onClick={() => handleToggleVector(vector.id)}
              className="flex items-start gap-3 cursor-pointer group select-none"
            >
              <span className={`material-symbols-outlined mt-1 transition-colors ${
                vector.completed ? 'text-[#3525cd]' : 'text-[#777587]'
              }`}>
                {vector.completed ? 'check_circle' : 'radio_button_unchecked'}
              </span>
              <span className={vector.completed ? 'text-[#1a1c1c]' : 'text-[#777587] line-through'}>
                {vector.title}
              </span>
            </li>
          ))}
        </ul>

        {/* AI Output Banner Notification */}
        {aiResponseText && (
          <div className="mt-8 p-4 bg-emerald-50 border border-emerald-200 text-emerald-900 rounded-lg text-sm flex items-start gap-3">
            <span className="material-symbols-outlined text-emerald-600 mt-0.5">auto_awesome</span>
            <div>
              <span className="font-bold block mb-1">Agent synthesized extension:</span>
              <p className="font-body-md text-xs">{aiResponseText}</p>
            </div>
          </div>
        )}

        {/* Floating CMD+K Prompt Bar */}
        <form
          onSubmit={handleRunCmdPrompt}
          className="fixed bottom-12 left-1/2 transform -translate-x-1/2 z-30 flex items-center bg-[#2f3130] text-[#f1f1f0] px-4 py-2.5 rounded-full shadow-xl border border-white/10 w-[90%] max-w-lg transition-all focus-within:ring-2 focus-within:ring-[#3525cd]"
        >
          <span className="material-symbols-outlined text-lg mr-2 text-[#c3c0ff]">
            temp_preferences_custom
          </span>
          <input
            type="text"
            value={cmdInput}
            onChange={(e) => setCmdInput(e.target.value)}
            placeholder="Ask Agent to expand on Growth Vectors..."
            className="bg-transparent border-none outline-none font-body-md text-sm text-white placeholder-gray-400 flex-1 pr-2"
          />
          <div className="flex items-center gap-1 bg-white/10 px-2 py-1 rounded text-xs font-label-caps text-gray-300 shrink-0">
            {isGenerating ? (
              <span className="material-symbols-outlined text-sm animate-spin">sync</span>
            ) : (
              <>
                <span className="material-symbols-outlined text-[13px]">keyboard_command_key</span>
                <span>K</span>
              </>
            )}
          </div>
        </form>
      </article>
    </div>
  );
};
