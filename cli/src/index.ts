#!/usr/bin/env node

import { Command } from 'commander';
import { submitCommand } from './commands/submit.js';
import { statusCommand } from './commands/status.js';
import { listCommand } from './commands/list.js';
import { feedbackCommand } from './commands/feedback.js';
import { getServerUrl } from './config.js';

const program = new Command();

program
  .name('zm-skill')
  .description('CLI tool for ZM Skill Collector — submit documents, manage skills, provide feedback')
  .version('0.1.0')
  .hook('preAction', () => {
    // Show which server we are talking to in verbose/debug scenarios
    if (process.env.DEBUG) {
      console.log(`[debug] Server: ${getServerUrl()}`);
    }
  });

program.addCommand(submitCommand);
program.addCommand(statusCommand);
program.addCommand(listCommand);
program.addCommand(feedbackCommand);

program.parse();
