import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurConverseAgent } from '../../model/converse-agent.model';
import { NotifierService } from 'angular-notifier';
import { TurConverseAgentService } from '../../service/converse-agent.service';
import { Router} from '@angular/router';

@Component({
  selector: 'converse-agent-list-page',
  templateUrl: './converse-agent-list-page.component.html'
})
export class TurConverseAgentListPageComponent implements OnInit {
  private turConverseAgents: Observable<TurConverseAgent[]>;

  constructor(
    private readonly notifier: NotifierService,
    private turConverseAgentService: TurConverseAgentService,
    private router: Router) {
    this.turConverseAgents = turConverseAgentService.query();
  }

  getTurConverseAgents(): Observable<TurConverseAgent[]> {

    return this.turConverseAgents;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
