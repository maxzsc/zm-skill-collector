import { Command } from 'commander';
import axios from 'axios';
import FormData from 'form-data';
import { createReadStream, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { getApiBaseUrl } from '../config.js';

export const submitCommand = new Command('submit')
  .description('Submit documents for skill extraction')
  .argument('[path]', 'Path to document file or directory')
  .option('--yuque <url>', 'Submit a Yuque document URL instead of a file')
  .action(async (path: string | undefined, options: { yuque?: string }) => {
    const ora = (await import('ora')).default;
    const chalk = (await import('chalk')).default;
    const baseUrl = getApiBaseUrl();

    if (options.yuque) {
      // Submit Yuque URL
      const spinner = ora('Submitting Yuque document...').start();
      try {
        const response = await axios.post(`${baseUrl}/submissions/yuque`, {
          url: options.yuque,
        });
        spinner.succeed('Yuque document submitted successfully');
        console.log(chalk.green(`Submission ID: ${response.data.id}`));
        console.log(chalk.dim(`Status: ${response.data.status}`));
        console.log(chalk.dim(`Track progress: zm-skill status ${response.data.id}`));
      } catch (error: any) {
        spinner.fail('Submission failed');
        const msg = error.response?.data?.message || error.message;
        console.error(chalk.red(`Error: ${msg}`));
        process.exit(1);
      }
      return;
    }

    if (!path) {
      console.error(chalk.red('Error: provide a file path or use --yuque <url>'));
      process.exit(1);
    }

    const resolvedPath = resolve(path);
    if (!existsSync(resolvedPath)) {
      console.error(chalk.red(`Error: file not found: ${resolvedPath}`));
      process.exit(1);
    }

    const spinner = ora('Uploading document...').start();
    try {
      const form = new FormData();
      form.append('file', createReadStream(resolvedPath));

      const response = await axios.post(`${baseUrl}/submissions`, form, {
        headers: form.getHeaders(),
        maxContentLength: Infinity,
        maxBodyLength: Infinity,
      });

      spinner.succeed('Document submitted successfully');
      console.log(chalk.green(`Submission ID: ${response.data.id}`));
      console.log(chalk.dim(`Status: ${response.data.status}`));
      console.log(chalk.dim(`Track progress: zm-skill status ${response.data.id}`));
    } catch (error: any) {
      spinner.fail('Upload failed');
      const msg = error.response?.data?.message || error.message;
      console.error(chalk.red(`Error: ${msg}`));
      process.exit(1);
    }
  });
