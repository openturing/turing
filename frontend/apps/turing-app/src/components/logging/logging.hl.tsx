import React from "react";

interface LogHighlighterProps {
    text: string;
}

export const LogHighlighter: React.FC<LogHighlighterProps> = ({ text }) => {
    const patterns = [
        // Severity levels
        { regex: /\b(ERROR|FATAL|SEVERE|FAILED)\b/gi, className: "text-red-500 font-bold" },
        { regex: /\b(WARN|WARNING)\b/gi, className: "text-yellow-500 font-bold" },
        { regex: /\b(INFO|SUCCESS|OK)\b/gi, className: "text-blue-500 font-bold" },
        { regex: /\b(DEBUG|TRACE|FINE)\b/gi, className: "text-purple-500 font-bold" },

        // Lifecycle events
        {
            regex: /\b(initializ\w*|start\w*|stop\w*|shutdown|ready|active|finish\w*|complet\w*)\b/gi,
            className: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 px-0.5 rounded border border-emerald-500/10 font-bold"
        },

        // Spring/AEM components
        { regex: /\b(DispatcherServlet|ContextLoaderListener|Servlet\w*|Filter\w*|Sling\w*)\b/g, className: "text-indigo-600 dark:text-indigo-400 font-semibold" },

        // Quoted strings
        { regex: /'([^']+)'|"([^"]+)"/g, className: "text-sky-600 dark:text-sky-400 font-mono italic" },

        // Java packages and classes
        { regex: /\b([a-z][a-z0-9_]*\.[a-z0-9_.]*[A-Z]\w*)\b/g, className: "text-amber-600 dark:text-amber-400 italic" },

        // JDBC and paths
        { regex: /\b(jdbc:[a-z0-9]+:[^ \n\t"']+)\b/gi, className: "text-sky-600 dark:text-sky-400 font-mono italic" },
        { regex: /\b([A-Z]:\\[^ \n\t]*)\b/gi, className: "text-sky-600 dark:text-sky-400 font-mono italic" },
        { regex: /(?:\s|'|^)(\/(?:[\w.-]+(?:\/[\w.-]+)*))\b/g, className: "text-sky-600 dark:text-sky-400 font-mono italic" },

        // Network
        { regex: /(\d{1,3}\.){3}\d{1,3}/g, className: "text-cyan-500" },
        { regex: /(https?:\/\/[^\s]+)/g, className: "text-blue-400 underline" },

        // AEM specific
        { regex: /\b(AUTHOR|PUBLISHING|PUBLISH)\b/g, className: "font-bold border-b border-dotted" },

        // User/System actions
        {
            regex: /\b(LOGIN|LOGOUT|AUTHENTICATED|AUTHORIZING|GRANTED|DENIED|CONNECTING|CONNECTED|DISCONNECTED)\b/gi,
            className: "text-amber-600 dark:text-amber-400 font-semibold italic"
        }
    ];

    const combinedRegex = new RegExp(patterns.map(p => p.regex.source).join("|"), "g");
    const nodes: React.ReactNode[] = [];
    let lastIndex = 0;
    let match;

    while ((match = combinedRegex.exec(text)) !== null) {
        const matchText = match[0];
        const matchIndex = match.index;

        if (matchIndex > lastIndex) {
            nodes.push(text.substring(lastIndex, matchIndex));
        }

        const style = patterns.find(p => {
            p.regex.lastIndex = 0;
            return p.regex.test(matchText);
        });

        nodes.push(
            <span key={matchIndex} className={style?.className}>
                {matchText}
            </span>
        );

        lastIndex = combinedRegex.lastIndex;
    }

    if (lastIndex < text.length) {
        nodes.push(text.substring(lastIndex));
    }

    return (
        <span className="font-mono text-sm leading-relaxed whitespace-pre-wrap">
            {nodes.length > 0 ? nodes : text}
        </span>
    );
};