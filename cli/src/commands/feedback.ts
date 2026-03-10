import { Command } from 'commander';
import axios from 'axios';
import { getApiBaseUrl } from '../config.js';

const VALID_TYPES = ['useful', 'misleading', 'outdated'] as const;
type FeedbackType = (typeof VALID_TYPES)[number];

export const feedbackCommand = new Command('feedback')
  .description('Submit feedback on a skill')
  .argument('<name>', 'Skill name')
  .argument('<type>', `Feedback type: ${VALID_TYPES.join(', ')}`)
  .option('--comment <text>', 'Optional comment')
  .action(async (name: string, type: string, options: { comment?: string }) => {
    const chalk = (await import('chalk')).default;
    const baseUrl = getApiBaseUrl();

    if (!VALID_TYPES.includes(type as FeedbackType)) {
      console.error(
        chalk.red(`Error: invalid feedback type "${type}". Must be one of: ${VALID_TYPES.join(', ')}`)
      );
      process.exit(1);
    }

    try {
      const body: Record<string, string> = {
        skillName: name,
        type,
      };
      if (options.comment) {
        body.comment = options.comment;
      }

      await axios.post(`${baseUrl}/feedback`, body);
      console.log(chalk.green(`Feedback "${type}" submitted for skill "${name}"`));
    } catch (error: any) {
      const msg = error.response?.data?.message || error.message;
      console.error(chalk.red(`Error: ${msg}`));
      process.exit(1);
    }
  });
